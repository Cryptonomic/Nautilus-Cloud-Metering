#include <errno.h>
#include <jansson.h>
#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#define UNKNOWN "unknown"
#define VERSION "01"
#define PREAMBLE "meter.F3."
#define DELIMITER "\r\n"

typedef struct {
  ngx_str_t fdpath; // This cannot be more than 108 chars in length (see sockaddr_un doc)
  ngx_flag_t enable;
  ngx_uint_t iotimeout;
  ngx_array_t *extractheaders;
  ngx_str_t servername;
} metered_access_loc_conf_t;

static ngx_int_t metered_access_handler(ngx_http_request_t *r);
static void *metered_access_create_loc_conf(ngx_conf_t *cf);
static char *metered_access_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child);
static ngx_int_t metered_access_init(ngx_conf_t *cf);
static char *construct_access_check_request(ngx_http_request_t *r,
                                            metered_access_loc_conf_t *hlcf);

static ngx_command_t metered_access_commands[] = {
    {
        ngx_string("metered_access"), // Takes value on or off
        NGX_HTTP_LOC_CONF |
            NGX_CONF_FLAG,           // Where all this can appear and what is it
        ngx_conf_set_flag_slot,      // Fn to parse the value from the conf
        NGX_HTTP_LOC_CONF_OFFSET,    // What level to apply this value enventually
        offsetof(metered_access_loc_conf_t,
                 enable),           // Offset in the data structure
        NULL                        // Post processor function
    },
    {
      ngx_string("metered_access_ipc_fd_path"),
      NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1, ngx_conf_set_str_slot,
      NGX_HTTP_LOC_CONF_OFFSET, offsetof(metered_access_loc_conf_t, fdpath),
      NULL
    },
    {
      ngx_string("metered_access_io_timeout"),
      NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1, ngx_conf_set_num_slot,
      NGX_HTTP_LOC_CONF_OFFSET, offsetof(metered_access_loc_conf_t, iotimeout),
      NULL
    },
    {
      ngx_string("metered_access_extract_header"),
      NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1, ngx_conf_set_str_array_slot,
      NGX_HTTP_LOC_CONF_OFFSET, offsetof(metered_access_loc_conf_t, extractheaders),
      NULL
    },
    {
      ngx_string("metered_access_servername"),
      NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1, ngx_conf_set_str_slot,
      NGX_HTTP_LOC_CONF_OFFSET, offsetof(metered_access_loc_conf_t, servername),
      NULL
    },
    ngx_null_command
  };

static ngx_http_module_t metered_access_module_ctx = {
    NULL,                           /* preconfiguration */
    metered_access_init,            /* postconfiguration */
    NULL,                           /* create main configuration */
    NULL,                           /* init main configuration */
    NULL,                           /* create server configuration */
    NULL,                           /* merge server configuration */
    metered_access_create_loc_conf, /* create location configuration */
    metered_access_merge_loc_conf   /* merge location configuration */

};

ngx_module_t metered_access_module = {
    NGX_MODULE_V1,
    &metered_access_module_ctx, /* module context */
    metered_access_commands,    /* module directives */
    NGX_HTTP_MODULE,            /* module type */
    NULL,                       /* init master */
    NULL,                       /* init module */
    NULL,                       /* init process */
    NULL,                       /* init thread */
    NULL,                       /* exit thread */
    NULL,                       /* exit process */
    NULL,                       /* exit master */
    NGX_MODULE_V1_PADDING

};

static ngx_table_elt_t *search_headers_by_name(ngx_http_request_t *r,
                                               u_char *name, size_t len) {
  ngx_list_part_t *part;
  ngx_table_elt_t *h;
  ngx_uint_t i;

  part = &r->headers_in.headers.part;
  h = part->elts;

  for (i = 0;; ++i) {
    if (i >= part->nelts) {
      if (part->next == NULL) {
        break;
      }

      part = part->next;
      h = part->elts;
      i = 0;
    }

    if (len != h[i].key.len || ngx_strcasecmp(name, h[i].key.data) != 0) {
      continue;
    }

    return &h[i];
  }

  return NULL;
}

json_t * wrapHeader(const char * name, size_t nlen, const char * value, size_t vlen) {
  json_t * header = json_object();
  json_object_set_new(header, "name" , json_stringn(name , nlen));
  json_object_set_new(header, "value", json_stringn((const char *) value, vlen));
  return header;
}

/*
 * Constructs an json IPC request object from the incoming HTTP request
 */
static char *construct_access_check_request(ngx_http_request_t *r,
                                            metered_access_loc_conf_t *hlcf) {
  char *s = NULL; // NOTE: this needs to be disposed by caller
  ngx_table_elt_t *ua = r->headers_in.user_agent;

  json_t *root = json_object();
  json_t *json_arr = json_array();

  // The json_*_new fn set will automatically steal references. It is therefore
  // not required to manually json_decref() the object returned by json_string
  // fn.
  if(ua != NULL)
    json_object_set_new(root, "userAgent", json_stringn((const char *)ua->value.data,
      ua->value.len));
  else
    json_object_set_new(root, "userAgent", json_stringn((const char *) UNKNOWN,
      strlen(UNKNOWN)));
  json_object_set_new(root, "servername",
      json_stringn((const char *) hlcf->servername.data, hlcf->servername.len));
  json_object_set_new(root, "uri", json_stringn((const char *)r->uri.data, r->uri.len));
  json_object_set_new(root, "ip",
                      json_stringn((const char *)r->connection->addr_text.data,
                                   r->connection->addr_text.len));
  json_object_set_new( root, "headers", json_arr );

  // Extract optional headers as specified in nginx config
  if (hlcf->extractheaders != NULL) {
    ngx_str_t *hlist = hlcf->extractheaders->elts;
    for (ngx_uint_t i = 0; i < hlcf->extractheaders->nelts; ++i) {
      ngx_str_t *eh = &hlist[i];
      if (eh == NULL) {
        continue;
      }
      ngx_table_elt_t *fo =
          search_headers_by_name(r, eh->data, eh->len);
      if (fo != NULL) {
        json_array_append( json_arr, wrapHeader((const char *)eh->data, eh->len,
                                                (const char *)fo->value.data, fo->value.len  ) );
      }
    }
  }

  s = json_dumps(root, 0);

  // Release the root object
  json_decref(root);

  // Remember to free this string
  return s;
}

static ngx_int_t metered_access_handler(ngx_http_request_t *r) {

  metered_access_loc_conf_t *hlcf;
  hlcf = ngx_http_get_module_loc_conf(r, metered_access_module);

  if (hlcf->enable != 1) {
    ngx_log_debug0(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
                   "MAM: Disabled for loc");
    return NGX_OK;
  }

  ngx_log_debug0(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
                 "MAM: Enabled for loc");

  // Construct the request
  char *authrequest = construct_access_check_request(r, hlcf);
  size_t requestlen = strlen(authrequest);
  struct timeval tv;
  struct sockaddr_un addr;
  int fd, rc, errorcode;
  char response;

  if (authrequest == NULL) {
    // Retuning this instead of 403 is deliberate
    // 401 indicates to the client that it should retry after fixing the issue
    // which in this case would be a badly formed header (missing etc) 403 tends
    // to indicate an authorization problem, which generally means the client
    // needs priviliges.
    return NGX_HTTP_UNAUTHORIZED;
  }

  tv.tv_sec = hlcf->iotimeout;
  tv.tv_usec = 0;

  if ((fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
                  "MAM: Unable to create IPC socket");
    free(authrequest);
    return NGX_HTTP_INTERNAL_SERVER_ERROR;
  }

  if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (const char *)&tv, sizeof(tv)) !=  0) {
    errorcode = errno;
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
                  "MAM: Unable to set socket options, reason = %s",
                  strerror(errorcode));
    free(authrequest);
    return NGX_HTTP_INTERNAL_SERVER_ERROR;
  }

  memset(&addr, 0, sizeof(addr));
  strncpy(addr.sun_path, (const char *)hlcf->fdpath.data, hlcf->fdpath.len);
  addr.sun_family = AF_UNIX;

  if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) == -1) {
    errorcode = errno;
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
                  "MAM: Unable to connect to fd `%s`, reason = %s",
                  addr.sun_path, strerror(errorcode));
    close(fd);
    free(authrequest);
    return NGX_HTTP_INTERNAL_SERVER_ERROR;
  }

  // Needs work
  char buf[4096];
  int rrlen;
  rrlen = sprintf(buf, "%s%s%s", PREAMBLE, VERSION, DELIMITER);
  rc = write(fd, buf, rrlen);
  rrlen = sprintf(buf, "Length: %ld%s", requestlen, DELIMITER);

  rc = write(fd, buf, rrlen);

  rc = write(fd, authrequest, requestlen);
  errorcode = errno;
  if (rc < 0 ||
      rc != (ssize_t)requestlen) { // NOTE: we don't handle partial writes
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
                  "MAM: Unable to write to fd `%s` message `%s`, reason = %s",
                  addr.sun_path, authrequest, strerror(errorcode));
    free(authrequest);
    close(fd);
    return NGX_HTTP_INTERNAL_SERVER_ERROR;
  }

  free(authrequest);
  ngx_log_debug1(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
                 "MAM: Wrote %d bytes", rc);

  // Read a single char back
  rc = read(fd, &response, 1);
  errorcode = errno;
  if (rc != 1) {
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
                  "MAM: Unable to read response from fd `%s`, reason = %s",
                  addr.sun_path, strerror(errorcode));
    close(fd);
    return NGX_HTTP_INTERNAL_SERVER_ERROR;
  }

  // Clean up
  rc = close(fd);
  errorcode = errno;
  if (rc < 0) {
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
                  "MAM: Unable to close to fd `%s`, reason = %s", addr.sun_path,
                  strerror(errorcode));
    return NGX_HTTP_INTERNAL_SERVER_ERROR;
  }

  // Now process the reponse
  ngx_log_debug1(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
                 "MAM: Authorization process responded with `%c`", response);

  if (response != 'Y') {
    return NGX_HTTP_FORBIDDEN;
  }

  return NGX_OK;
}

static void *metered_access_create_loc_conf(ngx_conf_t *cf) {

  metered_access_loc_conf_t *conf;

  // Zeros everything
  conf = ngx_pcalloc(cf->pool, sizeof(metered_access_loc_conf_t));

  if (conf == NULL)
    return NULL;

  conf->enable         = NGX_CONF_UNSET; // The default is set via merge fns
  conf->iotimeout      = NGX_CONF_UNSET_UINT;
  conf->extractheaders = NGX_CONF_UNSET_PTR;
  return conf;
}

static char *metered_access_merge_loc_conf(ngx_conf_t *cf, void *parent,
                                           void *child) {

  metered_access_loc_conf_t *prev = parent;
  metered_access_loc_conf_t *conf = child;

  // ngx_conf_merge_ptr_value(conf->hash, prev->hash, NULL);
  ngx_conf_merge_str_value(conf->fdpath, prev->fdpath, "");
  ngx_conf_merge_str_value(conf->servername, prev->servername, "unset");
  ngx_conf_merge_value(conf->enable, prev->enable, 0);
  ngx_conf_merge_uint_value(conf->iotimeout, prev->iotimeout, 5);
  ngx_conf_merge_ptr_value(conf->extractheaders, prev->extractheaders, NULL);

  return NGX_CONF_OK;
}

static ngx_int_t metered_access_init(ngx_conf_t *cf) {

  ngx_http_handler_pt *h;
  ngx_http_core_main_conf_t *cmcf;

  cmcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_core_module);
  h = ngx_array_push(&cmcf->phases[NGX_HTTP_ACCESS_PHASE].handlers);

  if (h == NULL) {
    return NGX_ERROR;
  }

  *h = metered_access_handler;

  return NGX_OK;
}
