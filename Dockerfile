FROM nolan/android
RUN adduser --system --group build --shell /bin/sh
ADD . /usr/local/src/app
WORKDIR /usr/local/src/app
ENV HOME /home/build
CMD chown -R build /usr/local/src/app && \
  su build -c "chmod +x gradlew && ./gradlew assembleRelease"