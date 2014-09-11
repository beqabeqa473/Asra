FROM nolan/android
RUN curl -L http://dl.bintray.com/sbt/debian/sbt-0.13.5.deb -o /tmp/sbt.deb && \
  dpkg -i /tmp/sbt.deb && \
  rm /tmp/sbt.deb && \
  adduser --system --group build --shell /bin/sh
ADD . /usr/local/src/app
WORKDIR /usr/local/src/app
ENV HOME /home/build
CMD chown -R build /usr/local/src/app && \
  su build -c "sbt android:package-release"