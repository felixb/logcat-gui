#!/bin/sh

path="$(dirname $0)"

if (which adb >/dev/null 2>/dev/null) then
  echo found adb: $(which adb)
else
  adb=$(locate -b -r ^adb$ 2>/dev/null | grep platform-tools | head -n1)
  if [ -n "${adb}" ] && [ -x "${adb}" ] ; then
    echo found adb: ${adb}
    PATH="$(dirname ${adb}):$PATH"
    export PATH
  else
    echo "warning: adb not found"
  fi
fi

java -jar "${path}/LogCat-GUI.jar"
