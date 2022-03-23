services {
  check {
    http = "http://127.78.0.5:10000/health",
    method = "GET",
    timeout = "1s"
    interval = "5s"
  }
  connect {
    sidecar_service {
      proxy {
        local_service_address = "127.78.0.5"
        upstreams = [
          {
            destination_name = "carbonio-mailbox"
            local_bind_address = "127.78.0.5"
            local_bind_port = 20000
          }
        ]
      }
    }
  }
  name = "carbonio-user-management"
  port = 10000
}
