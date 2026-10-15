import { useEffect, useRef, useState } from "react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts";

import {
  getOverview,
  getRecentEvents,
  getUsers,
} from "../services/analyticsService";

import "../styles/Dashboard.css";

const COLORS = ["#7650db", "#299b79", "#3679df", "#df8a2f", "#df5c75"];

const getEventType = (event) =>
  String(event.eventType || event.event_type || "UNKNOWN").toUpperCase();

const getUserName = (event) =>
  event.userName ||
  event.username ||
  event.user_name ||
  "Unknown";

const getPageUrl = (event) =>
  event.pageUrl || event.page_url || "—";

const getEventTime = (event) =>
  event.createdAt || event.created_at || event.timestamp || "";

function Analytics() {
  const [data, setData] = useState(null);
  const [events, setEvents] = useState([]);
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [selectedMetric, setSelectedMetric] = useState("");

  const detailsRef = useRef(null);

  // Load overview, events, and registered users.
  useEffect(() => {
    let cancelled = false;

    async function loadAnalytics() {
      try {
        const [overview, recentEvents] = await Promise.all([
          getOverview(),
          getRecentEvents(),
        ]);

        if (cancelled) return;

        setData(overview);
        setEvents(Array.isArray(recentEvents) ? recentEvents : []);

        // Load the complete registered-users list from the backend.
        try {
          const allUsers = await getUsers();

          if (cancelled) return;
if (Array.isArray(allUsers)) {
  const sortedUsers = [...allUsers].sort(
    (a, b) => Number(a.id) - Number(b.id)
  );

  setUsers(sortedUsers);
  setError("");
} else {
            setUsers([]);
            setError(
              "The users API did not return a valid list. Check getUsers() in analyticsService.js."
            );
          }
        } catch (userError) {
          console.error("Unable to load registered users:", userError);

          if (!cancelled) {
            setUsers([]);
            setError(
              userError.message ||
                "Analytics loaded, but registered users could not be loaded."
            );
          }
        }
      } catch (err) {
        console.error("Unable to load analytics:", err);

        if (!cancelled) {
          setError(
            err.message ||
              "Unable to load analytics data. Check the backend."
          );
        }
      }
    }

    loadAnalytics();

    return () => {
      cancelled = true;
    };
  }, []);

  // Scroll to details when a metric is selected.
  useEffect(() => {
    if (selectedMetric && detailsRef.current) {
      detailsRef.current.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }
  }, [selectedMetric]);

  const metrics = [
    {
      id: "users",
      label: "Total Users",
      value: data?.totalUsers ?? "—",
      description: "Registered users · Click to view",
    },
    {
      id: "sessions",
      label: "Active Sessions",
      value: data?.activeSessions ?? "—",
      description: "Currently active · Click to view",
    },
    {
      id: "pageViews",
      label: "Page Views",
      value: data?.pageViews ?? "—",
      description: "Recorded page views · Click to view",
    },
    {
      id: "events",
      label: "Total Events",
      value: data?.totalEvents ?? "—",
      description: "Recorded events · Click to view",
    },
  ];

  // Data for the platform metrics chart.
  const overviewChart = data
    ? [
        { name: "Users", count: Number(data.totalUsers) || 0 },
        { name: "Sessions", count: Number(data.activeSessions) || 0 },
        { name: "Page Views", count: Number(data.pageViews) || 0 },
        { name: "Events", count: Number(data.totalEvents) || 0 },
      ]
    : [];

  // Count event types for the charts.
  const eventCounts = events.reduce((counts, event) => {
    const type = getEventType(event);
    counts[type] = (counts[type] || 0) + 1;
    return counts;
  }, {});

  const eventChart = Object.entries(eventCounts).map(([name, value]) => ({
    name,
    value,
  }));

  // Detail records for each metric.
  // Registered users come directly from getUsers().
  const detailOptions = {
    users: {
      title: "All Registered Users",
      description:
        "Registered accounts retrieved from the customer_accounts database table.",
      records: users,
      type: "users",
    },

    sessions: {
      title: "Login and Logout Activity",
      description:
        "Login/logout events from the latest events returned by the API. These are event records, not complete session records.",
      records: events.filter((event) =>
        ["LOGIN", "LOGOUT"].includes(getEventType(event))
      ),
      type: "events",
    },

    pageViews: {
      title: "Page View Records",
      description: "Page views from the latest events returned by the API.",
      records: events.filter(
        (event) => getEventType(event) === "PAGE_VIEW"
      ),
      type: "events",
    },

    events: {
      title: "Latest Event Records",
      description: "The latest event records returned by your backend API.",
      records: events,
      type: "events",
    },
  };

  const selectedDetails = detailOptions[selectedMetric];

  return (
    <section className="dashboard">
      <div className="welcome-row">
        <div>
          <h2>Analytics Overview</h2>
          <p>Understand activity and usage across your platform.</p>
        </div>
      </div>

      {error && <p className="error-banner">{error}</p>}

      {/* Metric cards */}
      <div className="metrics-grid">
        {metrics.map((item, index) => (
          <button
            type="button"
            className={`metric-card analytics-metric-card ${
              selectedMetric === item.id ? "metric-card-selected" : ""
            }`}
            key={item.id}
            onClick={() =>
              setSelectedMetric((current) =>
                current === item.id ? "" : item.id
              )
            }
            aria-pressed={selectedMetric === item.id}
          >
            <div className="metric-top">
              <span
                className={`metric-icon ${
                  ["purple", "green", "blue", "orange"][index]
                }`}
              >
                {["♙", "◉", "▤", "⌁"][index]}
              </span>

              <span className="metric-indicator">↘</span>
            </div>

            <p className="metric-title">{item.label}</p>
            <h2 className="metric-value">{item.value}</h2>
            <p className="metric-description">{item.description}</p>

            <span className="metric-click-hint">
              {selectedMetric === item.id
                ? "Viewing details ↑"
                : "Click to view details ↓"}
            </span>
          </button>
        ))}
      </div>

      {/* Selected metric details */}
      {selectedDetails && (
        <div className="panel metric-details-panel" ref={detailsRef}>
          <div className="panel-heading">
            <div>
              <h3>{selectedDetails.title}</h3>
              <p className="panel-subtitle">
                {selectedDetails.description}
              </p>
            </div>

            <button
              type="button"
              className="close-details-button"
              onClick={() => setSelectedMetric("")}
            >
              ✕ Close
            </button>
          </div>

          <p className="details-count">
            {selectedDetails.records.length} records in this view
          </p>

          {selectedDetails.records.length === 0 ? (
            <p className="chart-empty">
              {selectedMetric === "users"
                ? "No registered users were returned by the users API."
                : "No matching records found in the latest API response."}
            </p>
          ) : (
            <div className="table-wrapper">
              <table className="details-table">
                <thead>
                  <tr>
                    {selectedDetails.type === "users" ? (
                      <>
                        <th>ID</th>
                        <th>NAME</th>
                        <th>EMAIL</th>
                        <th>ROLE</th>
                        <th>STATUS</th>
                        <th>REGISTERED</th>
                      </>
                    ) : (
                      <>
                        <th>EVENT</th>
                        <th>USER</th>
                        <th>PAGE URL</th>
                        <th>TIME</th>
                      </>
                    )}
                  </tr>
                </thead>

                <tbody>
                  {selectedDetails.records.map((record, index) => (
                    <tr
                      key={
                        selectedDetails.type === "users"
                          ? record.id ?? `user-${index}`
                          : `${getUserName(record)}-${getEventType(
                              record
                            )}-${index}`
                      }
                    >
                      {selectedDetails.type === "users" ? (
                        <>
                          <td>{record.id ?? "—"}</td>

                          <td>
                            {record.fullName ||
                              record.full_name ||
                              record.name ||
                              "Unknown"}
                          </td>

                          <td>{record.email || "—"}</td>

                          <td>{record.role || "—"}</td>

                          <td>
                            {record.active === true
                              ? "Active"
                              : record.active === false
                                ? "Inactive"
                                : record.active === undefined
                                  ? "—"
                                  : String(record.active)}
                          </td>

                          <td>
                            {record.createdAt || record.created_at
                              ? new Date(
                                  record.createdAt || record.created_at
                                ).toLocaleString()
                              : "—"}
                          </td>
                        </>
                      ) : (
                        <>
                          <td>
                            <span className="event-type-label">
                              {getEventType(record)}
                            </span>
                          </td>

                          <td>{getUserName(record)}</td>

                          <td>{getPageUrl(record)}</td>

                          <td>
                            {getEventTime(record)
                              ? new Date(
                                  getEventTime(record)
                                ).toLocaleString()
                              : "—"}
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Platform metrics chart */}
      <div className="charts-grid">
        <div className="panel chart-panel">
          <h3>Platform Metrics</h3>
          <p className="panel-subtitle">
            Comparison of current database totals
          </p>

          <div className="chart-container">
            {data && (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={overviewChart}
                  margin={{ top: 15, right: 10, left: -15, bottom: 5 }}
                >
                  <CartesianGrid
                    strokeDasharray="3 3"
                    vertical={false}
                    stroke="#eceef5"
                  />

                  <XAxis
                    dataKey="name"
                    tick={{ fill: "#85899c", fontSize: 12 }}
                  />

                  <YAxis
                    allowDecimals={false}
                    tick={{ fill: "#85899c", fontSize: 12 }}
                  />

                  <Tooltip />

                  <Bar
                    dataKey="count"
                    name="Count"
                    fill="#7650db"
                    radius={[6, 6, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Event distribution chart */}
        <div className="panel chart-panel">
          <h3>Event Distribution</h3>
          <p className="panel-subtitle">
            Breakdown of the latest events returned by the API
          </p>

          <div className="chart-container">
            {eventChart.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={eventChart}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="45%"
                    outerRadius={90}
                    innerRadius={48}
                    paddingAngle={3}
                  >
                    {eventChart.map((entry, index) => (
                      <Cell
                        key={entry.name}
                        fill={COLORS[index % COLORS.length]}
                      />
                    ))}
                  </Pie>

                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <p className="chart-empty">No event data available.</p>
            )}
          </div>
        </div>
      </div>

      {/* Event type breakdown */}
      <div className="panel chart-panel event-breakdown">
        <h3>Event Type Breakdown</h3>
        <p className="panel-subtitle">
          Number of records by event type in the current API response
        </p>

        <div className="breakdown-list">
          {eventChart.map((event, index) => (
            <div className="breakdown-item" key={event.name}>
              <span
                className="breakdown-dot"
                style={{
                  background: COLORS[index % COLORS.length],
                }}
              />

              <span className="breakdown-name">{event.name}</span>
              <strong>{event.value}</strong>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export default Analytics;