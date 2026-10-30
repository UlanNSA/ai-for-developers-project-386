import "@typespec/http";
import "./errors.tsp";
import "./owner.tsp";

using Http;

@service(#{ title: "Calendar API" })
@server("http://localhost:8080", "Local development")
namespace CalendarApi;

model HealthResponse {
  status: string;
}

@get
@route("/api/health")
op getHealth(): HealthResponse;
