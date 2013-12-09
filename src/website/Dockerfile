FROM ubuntu:12.10
RUN echo "deb http://archive.ubuntu.com/ubuntu quantal main universe" > /etc/apt/sources.list && \
  apt-get update && \
  apt-get upgrade -y && \
  apt-get install -y software-properties-common && \
  add-apt-repository ppa:brightbox/ruby-ng-experimental && \
  apt-get update && \
  apt-get install -y --no-install-recommends build-essential ruby2.0 ruby2.0-dev asciidoc pandoc rsync && \
  apt-get clean && \
  gem2.0 install --no-ri --no-rdoc bundler && \
  locale-gen en_US.UTF-8
ADD . /usr/local/src/website
CMD cd /usr/local/src/website && \
  bundle install --without=development && \
  bundle exec nanoc && \
  bundle exec nanoc deploy
