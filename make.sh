#!/usr/bin/env bash

BASEDIR=`pwd`
NGINX_VERSION="1.17.8"
NGINX_DOWNLOAD_URL="https://nginx.org/download/nginx-$NGINX_VERSION.tar.gz"
BUILD_DIR="$BASEDIR/target"
NGINX_BUILD_DIR="$BUILD_DIR/nginx-$NGINX_VERSION"

function clean {
    echo "Cleaning ..."
    rm -rf "$BUILD_DIR" || true
}

function setup {
    echo "Setting up ..."
    mkdir -p "$BUILD_DIR"
    tar xzf "nginx-$NGINX_VERSION.tar.gz" -C "$BUILD_DIR"
    sbt update
}

function download {
    echo "Downloading ..."
    local src_file="nginx-$NGINX_VERSION.tar.gz"
    if [[ ! -f ${src_file} ]]; then
        echo "Fetching: $NGINX_DOWNLOAD_URL"
        wget "$NGINX_DOWNLOAD_URL" -O "$BASEDIR/$src_file"
        local status=$?
        if [[ $status -ne 0 ]]; then
            echo "Error downloading nginx source"
            rm -f "$BASEDIR/$src_file"
            exit 1
        fi
    else
        echo "Skipping, file ($src_file) exists."
    fi
}

function compile {
    echo "Compiling ..."
    cd "$NGINX_BUILD_DIR"
    ./configure --with-compat --add-dynamic-module="$BASEDIR/src/main/c/"  --with-ld-opt="-ljansson" --with-debug
    make modules -ljansson
    local cstatus=$?
    if [[ $cstatus -ne 0 ]]; then
        echo "Error compiling module source"
        exit 1
    fi
    cp objs/metered_access_module.so "$BUILD_DIR"
    cd "$BASEDIR"
    sbt 'set test in assembly := {}' assembly
}

function install {
    echo "Installing ..."
    sudo cp "$BUILD_DIR/metered_access_module.so" /etc/nginx/modules/metered_access_module.so
}

function test {
    echo "Running Tests ..."
    sbt test
}

case $1 in
  "")
    clean
    download
    setup
    compile
    ;;

  clean|download|setup|compile|test|install)
    $1
    ;;

  *)
    echo "Unknown command. Use ./make.sh [clean|download|setup|compile|test|install]"
    ;;
esac

