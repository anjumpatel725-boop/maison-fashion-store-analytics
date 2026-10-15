
import { useEffect, useMemo, useState } from "react";
import { getRecentEvents } from "../services/analyticsService";
import RecentEvents from "../components/RecentEvents";
import "../styles/Dashboard.css";

function Events() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [eventType, setEventType] = useState("ALL");

  async function loadEvents() {
    setLoading(true);

    try {
      const data = await getRecentEvents();
      setEvents(data);
      setError("");
    } catch (err) {
      console.error(err);
      setError("Unable to load events. Check the backend.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadEvents();
  }, []);

  const eventTypes = useMemo(
    () => [...new Set(events.map((event) => event.eventType).filter(Boolean))].sort(),
    [events]
  );

  const filteredEvents = useMemo(() => {
    const query = search.trim().toLowerCase();

    return events.filter((event) => {
      const matchesType =
        eventType === "ALL" || event.eventType === eventType;

      const searchableText = [
        event.eventType,
        event.userName,
        event.pageUrl,
        event.id,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return matchesType && searchableText.includes(query);
    });
  }, [events, search, eventType]);

  return (
    <section className="dashboard">
      <div className="welcome-row">
        <div>
          <h2>Event Explorer</h2>
          <p>Search and filter activity recorded by your platform.</p>
        </div>

        <button className="refresh-button" onClick={loadEvents}>
          ↻ Refresh events
        </button>
      </div>

      {error && <p className="error-banner">{error}</p>}

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h3>Events Activity</h3>
            <p>Search by event, user, URL or event ID</p>
          </div>

          <span className="event-count">
            {filteredEvents.length} of {events.length}
          </span>
        </div>

        <div className="event-filters">
          <div className="event-search">
            <span>⌕</span>
            <input
              type="search"
              placeholder="Search events, users, URLs..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <select
            className="event-type-select"
            value={eventType}
            onChange={(e) => setEventType(e.target.value)}
          >
            <option value="ALL">All event types</option>
            {eventTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>

          <button
            className="refresh-button"
            onClick={() => {
              setSearch("");
              setEventType("ALL");
            }}
          >
            Clear filters
          </button>
        </div>

        {loading ? (
          <p className="loading-events">Loading events...</p>
        ) : (
          <RecentEvents events={filteredEvents} />
        )}

        <p className="panel-subtitle events-footnote">
          Showing records from the latest 10 events returned by the backend.
        </p>
      </div>
    </section>
  );
}

export default Events;
