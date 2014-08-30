#!/bin/sh

set -e
$ANDROID_HOME/tools/lint --disable MissingTranslation,UnusedResources,Typos,ExportedContentProvider,IconLocation --classpath target/scala-2.10/classes/info --resources src/main/res .
