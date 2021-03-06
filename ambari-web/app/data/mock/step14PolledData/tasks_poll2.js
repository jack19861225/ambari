module.exports = {
  "href": "http://localhost:8080/api/clusters/mycluster/requests/1",
  "Requests": {
    "id": 1
  },
  "tasks": [
    {
      "href": "http://localhost:8080/api/clusters/mycluster/requests/1/tasks/1",
      "Tasks": {
        "id": "1",
        "attempt_cnt": "0",
        "exit_code": "999",
        "stdout": "",
        "status": "IN_PROGRESS",
        "command": "INSTALL",
        "start_time": "-1",
        "role": "DATANODE",
        "stderr": "",
        "host_name": "localhost.localdomain",
        "stage_id": "1"
      }
    },
    {
      "href": "http://localhost:8080/api/clusters/mycluster/requests/1/tasks/2",
      "Tasks": {
        "id": "2",
        "attempt_cnt": "0",
        "exit_code": "999",
        "stdout": "",
        "status": "QUEUED",
        "command": "INSTALL",
        "start_time": "-1",
        "role": "NAMENODE",
        "stderr": "",
        "host_name": "localhost.localdomain",
        "stage_id": "1"
      }
    },
    {
      "href": "http://localhost:8080/api/clusters/mycluster/requests/1/tasks/3",
      "Tasks": {
        "id": "3",
        "attempt_cnt": "0",
        "exit_code": "999",
        "stdout": "",
        "status": "QUEUED",
        "command": "INSTALL",
        "start_time": "-1",
        "role": "SECONDARY_NAMENODE",
        "stderr": "",
        "host_name": "host2",
        "stage_id": "1"
      }
    }
  ]
}
