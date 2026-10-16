
function RecentEvents({ events = [] }) {
  function formatDate(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return String(value);

  return date.toLocaleString("en-IN", {
    timeZone: "Asia/Kolkata",
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

  return (
    <div className="events-table-wrapper">
      <table className="events-table">
        <thead>
          <tr>
            <th>EVENT</th>
            <th>USER</th>
            <th>PAGE URL</th>
            <th>TIME</th>
          </tr>
        </thead>

        <tbody>
          {events.length === 0 ? (
            <tr>
              <td colSpan="4" className="empty-state">
                No matching events found.
              </td>
            </tr>
          ) : (
            events.map((event, index) => (
              <tr key={event.id ?? index}>
                <td>
                  <span
                    className={`event-badge ${String(
                      event.eventType ?? ""
                    ).toLowerCase()}`}
                  >
                    {event.eventType ?? "UNKNOWN"}
                  </span>
                </td>
                <td className="user-cell">
                  {event.userName ?? "Unknown user"}
                </td>
                <td className="url-cell" title={event.pageUrl ?? ""}>
                  {event.pageUrl || "—"}
                </td>
                <td className="time-cell">
                  {formatDate(event.createdAt)}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default RecentEvents;
