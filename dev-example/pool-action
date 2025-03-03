#!/bin/bash

function usage() {
  cat >&2 <<EOF
Usage: $0 pool-name action
Applys action to all VMs in the pool.

  --context                   Context to be passed to kubectl (required)
  -n, --namespace             Namespace to be passed to kubectl
  
Action is one of "start", "stop", "delete" or "delete-disks"

Defaults for context and namespace are read from .vm-operator-cmd.rc.
EOF
  exit 1  
}

unset pool
unset action
unset context
namespace=default

if [ -r .vm-operator-cmd.rc ]; then
  . .vm-operator-cmd.rc
fi

while [ "$#" -gt 0 ]; do
  case "$1" in
    --context) shift; context="$1";;
    --context=*) IFS='=' read -r option value <<< "$1"; context="$value";;
    -n|--namespace) shift; namespace="$1";;
    -*) echo >&2 "Unknown option: $1"; exit 1;;
    *) if [ ! -v pool ]; then
         pool="$1"
       elif [ ! -v action ]; then
         action="$1"
       else
         usage
       fi;;
  esac
  shift
done

if [ ! -v pool -o ! -v "action" -o ! -v context ]; then
  echo >&2 "Missing arguments or context not set."
  echo >&2
  usage
fi
case "$action" in
  "start"|"stop"|"delete"|"delete-disks") ;;
  *) usage;;
esac

kubectl --context="$context" -n "$namespace" get vms -o json \
  | jq -r '.items[] | select(.spec.pools | contains(["'${pool}'"])) | .metadata.name' \
| while read vmName; do
    case "$action" in
      start) kubectl --context="$context" -n "$namespace" patch vms "$vmName" \
        --type='merge' -p '{"spec":{"vm":{"state":"Running"}}}';;
      stop) kubectl --context="$context" -n "$namespace" patch vms "$vmName" \
        --type='merge' -p '{"spec":{"vm":{"state":"Stopped"}}}';;
      delete) kubectl --context="$context" -n "$namespace" delete vm/"$vmName";;
      delete-disks) kubectl --context="$context" -n "$namespace" delete \
        pvc -l app.kubernetes.io/instance="$vmName" ;;
    esac
done
