#!/bin/bash

# blocks until the test is complete.

MYDIR=$(dirname $0)
source ${MYDIR}/env.sh

echo "Waiting for tests to complete...zzz..."

function poll_wait() {
  sleep 180
}

function ssh_testbox() {
  local command=$1
  ssh -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -o "LogLevel=ERROR" -i ~/.orca/id_rsa  splunk@${TESTBOX_HOST} "$command"
}

while [ 1 == 1 ] ; do
  ssh_testbox "cat /tmp/progress.txt"
  RUNNING=$(ssh_testbox "cat /tmp/tests-running")
  if [ "$RUNNING" == "0" ] ; then
    break
  fi
  poll_wait
done

echo "Test pass is complete."

echo "Printing out test logs:"
ssh_testbox "cat /tmp/test-log"

echo "Remote results:"
ssh_testbox "find results"
