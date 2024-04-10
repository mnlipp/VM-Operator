#!/bin/sh

CONF_OPT="-f /run/etc/squid/squid.conf"
/usr/sbin/squid $CONF_OPT

inotifywait -m -e create -r /run/etc/squid |
  while read file_path file_event file_name; do
    if [ "$file_event" != "CREATE" ]; then
      continue
    fi
    if [ -r /run/squid/squid.pid ]; then
      echo "Reconfiguring squid"
      /usr/sbin/squid $CONF_OPT -k reconfigure
    else
      echo "Restarting squid"
      /usr/sbin/squid $CONF_OPT
    fi
    echo "Processed event"
  done
