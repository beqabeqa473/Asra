FROM ubuntu:quantal
RUN echo "deb http://archive.ubuntu.com/ubuntu quantal main universe" > /etc/apt/sources.list
RUN apt-get update
RUN apt-get upgrade -y
RUN apt-get install -y software-properties-common
RUN add-apt-repository ppa:brightbox/ruby-ng-experimental
RUN add-apt-repository ppa:nginx/stable
RUN apt-get update
RUN apt-get install -y build-essential ruby2.0 ruby2.0-dev asciidoc pandoc nginx; apt-get clean
RUN gem2.0 install --no-ri --no-rdoc bundler
ADD . /usr/local/src/website
RUN locale-gen en_US.UTF-8
RUN rm /etc/nginx/sites-enabled/default
RUN mkdir /var/www
ADD docker/nginx.conf /etc/nginx/sites-enabled/site
RUN cd /usr/local/src/website; bundle install --without=development; bundle exec nanoc; cp -R output/* /var/www
EXPOSE 80
CMD /etc/init.d/nginx start; tail -n+0 -f /var/log/nginx/access.log /var/log/nginx/error.log
