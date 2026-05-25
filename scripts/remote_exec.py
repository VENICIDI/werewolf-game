#!/usr/bin/env python3
"""Remote SSH executor for werewolf-game deployment."""
import sys
import paramiko

def run(host, user, password, command, timeout=120):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(host, port=22, username=user, password=password, timeout=15)
        stdin, stdout, stderr = client.exec_command(command, timeout=timeout, get_pty=True)
        out = stdout.read().decode('utf-8', errors='replace')
        err = stderr.read().decode('utf-8', errors='replace')
        exit_code = stdout.channel.recv_exit_status()
        if out:
            print(out, end='')
        if err:
            print(err, end='', file=sys.stderr)
        return exit_code
    finally:
        client.close()

if __name__ == '__main__':
    host = sys.argv[1]
    user = sys.argv[2]
    password = sys.argv[3]
    cmd = sys.argv[4]
    timeout = int(sys.argv[5]) if len(sys.argv) > 5 else 120
    code = run(host, user, password, cmd, timeout)
    sys.exit(code)
