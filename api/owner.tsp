import "@typespec/http";

using Http;

namespace CalendarApi;

model Owner {
  name: string;
  bio: string;

  /** IANA time zone id, e.g. Asia/Almaty */
  timeZone: string;
}

@get
@route("/api/owner")
op getOwner(): Owner | InternalErrorResponse;
