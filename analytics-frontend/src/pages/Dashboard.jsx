
import { useEffect, useMemo, useRef, useState } from "react";

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
  AreaChart,
  Area,
} from "recharts";

import {
  getOverview,
  getRecentEvents,
  getUsers,
  getRevenueTrend,
  getProducts,
} from "../services/analyticsService";

import MetricCard from "../components/MetricCard";
import RecentEvents from "../components/RecentEvents";
import "../styles/Dashboard.css";

const EVENT_COLORS = [
  "#7650db",
  "#159768",
  "#2873dc",
  "#e59a36",
  "#20b8a6",
  "#d95787",
];

function getEventType(event) {
  return String(
    event.eventType || event.event_type || "OTHER"
  ).toUpperCase();
}

function formatCurrency(amount) {
  const numericAmount = Number(amount ?? 0);

  return `₹${numericAmount.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function formatDateTime(value) {
  if (!value) return "—";

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? String(value)
    : date.toLocaleString("en-IN");
}

function getProductName(product) {
  return (
    product.name ||
    product.productName ||
    product.product_name ||
    product.title ||
    "Unnamed product"
  );
}

function getProductCategory(product) {
  return (
    product.category ||
    product.categoryName ||
    product.category_name ||
    "—"
  );
}

function getProductSku(product) {
  return (
    product.sku ||
    product.productCode ||
    product.product_code ||
    "—"
  );
}

function getProductPrice(product) {
  return (
    product.price ??
    product.salePrice ??
    product.sale_price ??
    product.unitPrice ??
    product.unit_price ??
    null
  );
}

function getProductStatus(product) {
  return (
    product.active ??
    product.isActive ??
    product.is_active ??
    null
  );
}

function Dashboard() {
  const [overview, setOverview] = useState(null);
  const [events, setEvents] = useState([]);
  const [registeredUsers, setRegisteredUsers] = useState([]);
  const [usersError, setUsersError] = useState("");

  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productsError, setProductsError] = useState("");
  const [productSearch, setProductSearch] = useState("");
  const [productCategory, setProductCategory] = useState("All");
  const [productRetry, setProductRetry] = useState(0);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedMetric, setSelectedMetric] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  const [revenueTrend, setRevenueTrend] = useState([]);
  const [revenueDays, setRevenueDays] = useState(7);
  const [revenueError, setRevenueError] = useState("");
  const [revenueLoading, setRevenueLoading] = useState(true);

  const detailsRef = useRef(null);

  // Load overview, recent events and registered users.
  async function loadDashboard() {
    setRefreshing(true);

    try {
      const [overviewData, eventsData] = await Promise.all([
        getOverview(),
        getRecentEvents(),
      ]);

      setOverview(overviewData);
      setEvents(Array.isArray(eventsData) ? eventsData : []);
      setError("");

      try {
        const usersData = await getUsers();

        if (Array.isArray(usersData)) {
          const sortedUsers = [...usersData].sort(
            (a, b) => Number(a.id) - Number(b.id)
          );

          setRegisteredUsers(sortedUsers);
          setUsersError("");
        } else {
          setRegisteredUsers([]);
          setUsersError(
            "The users API did not return a valid list."
          );
        }
      } catch (err) {
        console.error("Unable to load registered users:", err);

        setUsersError(
          err.message || "Unable to load registered users."
        );
      }
    } catch (err) {
      setError(
        err.message ||
          "Could not connect to the backend. Check that Spring Boot is running on port 8080."
      );

      console.error("Dashboard loading error:", err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  // Initial load and periodic dashboard refresh.
  useEffect(() => {
    loadDashboard();

    const interval = setInterval(() => {
      loadDashboard();
    }, 15000);

    return () => clearInterval(interval);
  }, []);

  // Load the selected revenue period.
  useEffect(() => {
    let cancelled = false;

    async function loadRevenue() {
      setRevenueLoading(true);
      setRevenueError("");

      try {
        const data = await getRevenueTrend(revenueDays);

        if (!cancelled) {
          setRevenueTrend(Array.isArray(data) ? data : []);
        }
      } catch (err) {
        if (!cancelled) {
          setRevenueTrend([]);
          setRevenueError(
            err.message || "Unable to load revenue trend."
          );
        }
      } finally {
        if (!cancelled) {
          setRevenueLoading(false);
        }
      }
    }

    loadRevenue();

    return () => {
      cancelled = true;
    };
  }, [revenueDays]);

  // Load products when the product details panel is opened.
  useEffect(() => {
    if (selectedMetric !== "products") return;

    let cancelled = false;

    async function loadProducts() {
      setProductsLoading(true);
      setProductsError("");

      try {
        const data = await getProducts();

        if (!cancelled) {
          setProducts(data);
        }
      } catch (err) {
        if (!cancelled) {
          setProducts([]);
          setProductsError(
            err.message || "Unable to load MAISON products."
          );
        }

        console.error("Products loading error:", err);
      } finally {
        if (!cancelled) {
          setProductsLoading(false);
        }
      }
    }

    loadProducts();

    return () => {
      cancelled = true;
    };
  }, [selectedMetric, productRetry]);

  // Open metric details and scroll into view.
  function handleMetricClick(metric) {
    setSelectedMetric(metric);

    setTimeout(() => {
      detailsRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }, 100);
  }

  // Distinct users found in the latest returned events.
  const uniqueEventUsers = useMemo(() => {
    const users = new Map();

    events.forEach((event) => {
      const name =
        event.userName ||
        event.username ||
        event.user_name;

      if (name) {
        users.set(String(name).toLowerCase(), {
          name,
          lastEvent: getEventType(event),
          lastPage:
            event.pageUrl || event.page_url || "—",
          time:
            event.createdAt ||
            event.created_at ||
            event.timestamp ||
            "",
        });
      }
    });

    return Array.from(users.values());
  }, [events]);

  // Event distribution for the pie chart.
  const eventDistribution = useMemo(() => {
    const counts = new Map();

    events.forEach((event) => {
      const type = getEventType(event);

      counts.set(type, (counts.get(type) || 0) + 1);
    });

    return Array.from(counts, ([name, value]) => ({
      name,
      value,
    })).sort((a, b) => b.value - a.value);
  }, [events]);

  // Current platform totals for the bar chart.
  const platformChartData = useMemo(
    () => [
      {
        name: "Users",
        value: Number(
          overview?.totalUsers ?? registeredUsers.length
        ),
      },
      {
        name: "Products",
        value: Number(overview?.totalProducts ?? 0),
      },
      {
        name: "Active Users",
        value: Number(overview?.activeUsers ?? 0),
      },
      {
        name: "Sessions",
        value: Number(overview?.activeSessions ?? 0),
      },
      {
        name: "Page Views",
        value: Number(overview?.pageViews ?? 0),
      },
      {
        name: "Events",
        value: Number(overview?.totalEvents ?? 0),
      },
    ],
    [overview, registeredUsers.length]
  );

  // Unique product categories.
  const productCategories = useMemo(() => {
    const categories = products
      .map((product) => getProductCategory(product))
      .filter((category) => category && category !== "—");

    return [
      "All",
      ...Array.from(new Set(categories)).sort((a, b) =>
        String(a).localeCompare(String(b))
      ),
    ];
  }, [products]);

  // Product search and category filtering.
  const filteredProducts = useMemo(() => {
    const query = productSearch.trim().toLowerCase();

    return products.filter((product) => {
      const name = getProductName(product);
      const category = getProductCategory(product);
      const sku = getProductSku(product);
      const id =
        product.id ??
        product.productId ??
        product.product_id ??
        "";

      const matchesSearch = [
        name,
        category,
        sku,
        id,
      ].some((value) =>
        String(value ?? "").toLowerCase().includes(query)
      );

      const matchesCategory =
        productCategory === "All" ||
        category === productCategory;

      return matchesSearch && matchesCategory;
    });
  }, [products, productSearch, productCategory]);

  // Definitions for the clickable metric details.
  const detailConfig = {
    products: {
      title: "MAISON Product Details",
      description:
        "Search, filter and inspect products from the MAISON Store database.",
      records: products,
      type: "products",
    },

    totalUsers: {
      title: "All Registered Users",
      description:
        "Registered users from the MAISON customer_accounts database, sorted by ID.",
      records: registeredUsers,
      type: "registeredUsers",
    },

    activeUsers: {
      title: "Recent User Activity",
      description:
        "Distinct users found in the latest returned events.",
      records: uniqueEventUsers,
      type: "activityUsers",
    },

    activeSessions: {
      title: "Recent Login and Logout Activity",
      description:
        "Login and logout events from the latest API response.",
      records: events.filter((event) =>
        ["LOGIN", "LOGOUT"].includes(getEventType(event))
      ),
      type: "events",
    },

    pageViews: {
      title: "Page View Records",
      description:
        "Page views from the latest returned events.",
      records: events.filter(
        (event) => getEventType(event) === "PAGE_VIEW"
      ),
      type: "events",
    },

    totalEvents: {
      title: "Recent Event Records",
      description:
        "Latest events returned by the backend.",
      records: events,
      type: "events",
    },

    totalRevenue: {
      title: "Revenue Details",
      description:
        "Revenue from successful payments for non-cancelled orders.",
      records: [],
      type: "revenue",
    },
  };

  if (loading) {
    return (
      <div className="status-message">
        Loading dashboard...
      </div>
    );
  }

  if (error && !overview) {
    return (
      <div className="status-message error-message">
        <h2>Connection issue</h2>
        <p>{error}</p>

        <button
          className="retry-button"
          onClick={loadDashboard}
          disabled={refreshing}
        >
          {refreshing ? "Retrying..." : "Try again"}
        </button>
      </div>
    );
  }

  const selectedDetails = detailConfig[selectedMetric];

  return (
    <section className="dashboard">
      {error && <p className="error-banner">{error}</p>}

      {/* Page heading */}
      <div className="welcome-row">
        <div>
          <h2>Welcome back, Anjum 👋</h2>
          <p>
            Here’s what’s happening with your platform today.
          </p>
        </div>

        <button
          className="refresh-button"
          onClick={loadDashboard}
          disabled={refreshing}
        >
          {refreshing ? "↻ Refreshing..." : "↻ Refresh data"}
        </button>
      </div>

      {/* Metric cards */}
      <div className="metrics-grid">
        <MetricCard
          title="Total Users"
          value={Number(
            overview?.totalUsers ?? registeredUsers.length
          )}
          icon="♙"
          description="Registered users · Click to view all"
          color="purple"
          onClick={() => handleMetricClick("totalUsers")}
          selected={selectedMetric === "totalUsers"}
        />

        <MetricCard
          title="Total Products"
          value={Number(overview?.totalProducts ?? 0)}
          icon="▦"
          description="Products in MAISON Store · Click to view"
          color="purple"
          onClick={() => handleMetricClick("products")}
          selected={selectedMetric === "products"}
        />

        <MetricCard
          title="Active Users"
          value={Number(overview?.activeUsers ?? 0)}
          icon="♙"
          description="Unique users active recently"
          color="blue"
          onClick={() => handleMetricClick("activeUsers")}
          selected={selectedMetric === "activeUsers"}
        />

        <MetricCard
          title="Active Sessions"
          value={Number(overview?.activeSessions ?? 0)}
          icon="◉"
          description="Currently active · Click to view"
          color="green"
          onClick={() => handleMetricClick("activeSessions")}
          selected={selectedMetric === "activeSessions"}
        />

        <MetricCard
          title="Page Views"
          value={Number(overview?.pageViews ?? 0)}
          icon="▤"
          description="Recorded page views"
          color="blue"
          onClick={() => handleMetricClick("pageViews")}
          selected={selectedMetric === "pageViews"}
        />

        <MetricCard
          title="Total Events"
          value={Number(overview?.totalEvents ?? 0)}
          icon="⌁"
          description="All recorded events"
          color="orange"
          onClick={() => handleMetricClick("totalEvents")}
          selected={selectedMetric === "totalEvents"}
        />

        <MetricCard
          title="Total Revenue"
          value={formatCurrency(overview?.totalRevenue)}
          icon="₹"
          description="Successful, non-cancelled orders · Click to view"
          color="green"
          onClick={() => handleMetricClick("totalRevenue")}
          selected={selectedMetric === "totalRevenue"}
        />
      </div>

      {/* Charts */}
      <div className="charts-grid">
        {/* Platform Activity */}
        <div className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <h3>Platform Activity</h3>
              <p>Current metrics from your backend</p>
            </div>
          </div>

          <div className="chart-container">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={platformChartData}
                margin={{
                  top: 10,
                  right: 12,
                  left: -15,
                  bottom: 5,
                }}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  vertical={false}
                  stroke="#e9edf5"
                />

                <XAxis
                  dataKey="name"
                  tick={{ fontSize: 11, fill: "#78839b" }}
                  axisLine={false}
                  tickLine={false}
                  interval={0}
                />

                <YAxis
                  allowDecimals={false}
                  tick={{ fontSize: 11, fill: "#78839b" }}
                  axisLine={false}
                  tickLine={false}
                />

                <Tooltip
                  cursor={{ fill: "#f4f1ff" }}
                  contentStyle={{
                    border: "1px solid #e7ebf3",
                    borderRadius: "10px",
                    boxShadow:
                      "0 5px 20px rgba(25,40,75,0.08)",
                  }}
                />

                <Bar
                  dataKey="value"
                  name="Count"
                  fill="#7650db"
                  radius={[6, 6, 0, 0]}
                  maxBarSize={48}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <p className="events-footnote">
            Values represent current totals, not historical daily activity.
          </p>
        </div>

        {/* Event Distribution */}
        <div className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <h3>Event Distribution</h3>
              <p>Event types in the latest API response</p>
            </div>
          </div>

          {eventDistribution.length === 0 ? (
            <div className="chart-empty">
              No event data available yet.
            </div>
          ) : (
            <>
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={eventDistribution}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="45%"
                      outerRadius={85}
                      innerRadius={48}
                      paddingAngle={3}
                    >
                      {eventDistribution.map((entry, index) => (
                        <Cell
                          key={`${entry.name}-${index}`}
                          fill={
                            EVENT_COLORS[
                              index % EVENT_COLORS.length
                            ]
                          }
                        />
                      ))}
                    </Pie>

                    <Tooltip
                      contentStyle={{
                        border: "1px solid #e7ebf3",
                        borderRadius: "10px",
                        boxShadow:
                          "0 5px 20px rgba(25,40,75,0.08)",
                      }}
                    />

                    <Legend
                      verticalAlign="bottom"
                      height={42}
                      iconType="circle"
                      wrapperStyle={{ fontSize: "11px" }}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </div>

              <p className="events-footnote">
                Based on {events.length} event records returned by the API.
              </p>
            </>
          )}
        </div>

        {/* Revenue Trend */}
        <div className="panel revenue-chart-panel">
          <div className="panel-heading revenue-heading">
            <div>
              <h3>Revenue Trend</h3>
              <p>Successful payments · Daily revenue in INR</p>
            </div>

            <div className="revenue-filters">
              <button
                type="button"
                className={revenueDays === 7 ? "active" : ""}
                aria-pressed={revenueDays === 7}
                onClick={() => setRevenueDays(7)}
              >
                7 Days
              </button>

              <button
                type="button"
                className={revenueDays === 30 ? "active" : ""}
                aria-pressed={revenueDays === 30}
                onClick={() => setRevenueDays(30)}
              >
                30 Days
              </button>
            </div>
          </div>

          <div className="revenue-summary">
            <div>
              <span>Total Revenue</span>
              <strong>
                {formatCurrency(overview?.totalRevenue)}
              </strong>
            </div>

            <span className="revenue-live-label">
              <span className="live-dot" />
              Backend data
            </span>
          </div>

          {revenueError ? (
            <div className="chart-empty">
              <p>{revenueError}</p>

              <button
                type="button"
                className="retry-button"
                onClick={() =>
                  setRevenueDays((days) => (days === 7 ? 30 : 7))
                }
              >
                Retry
              </button>
            </div>
          ) : revenueLoading ? (
            <div className="chart-empty">
              Loading revenue trend...
            </div>
          ) : revenueTrend.length === 0 ? (
            <div className="chart-empty">
              No revenue trend data was returned by the API.
            </div>
          ) : (
            <div className="revenue-chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart
                  data={revenueTrend}
                  margin={{
                    top: 12,
                    right: 18,
                    left: 8,
                    bottom: 4,
                  }}
                >
                  <defs>
                    <linearGradient
                      id="revenueGradient"
                      x1="0"
                      y1="0"
                      x2="0"
                      y2="1"
                    >
                      <stop
                        offset="0%"
                        stopColor="#20d6a0"
                        stopOpacity={0.35}
                      />
                      <stop
                        offset="95%"
                        stopColor="#20d6a0"
                        stopOpacity={0.01}
                      />
                    </linearGradient>
                  </defs>

                  <CartesianGrid
                    strokeDasharray="4 5"
                    vertical={false}
                    stroke="#294263"
                  />

                  <XAxis
                    dataKey="date"
                    tick={{ fill: "#a0b5d5", fontSize: 11 }}
                    axisLine={false}
                    tickLine={false}
                    minTickGap={12}
                  />

                  <YAxis
                    tickFormatter={(value) =>
                      `₹${Number(value).toLocaleString("en-IN")}`
                    }
                    tick={{ fill: "#a0b5d5", fontSize: 10 }}
                    axisLine={false}
                    tickLine={false}
                    width={78}
                  />

                  <Tooltip
                    formatter={(value) => [
                      formatCurrency(value),
                      "Revenue",
                    ]}
                    labelFormatter={(label) => `Date: ${label}`}
                    contentStyle={{
                      background: "#102748",
                      border: "1px solid #35547e",
                      borderRadius: "10px",
                      color: "#edf4ff",
                      boxShadow: "0 8px 24px rgba(0,0,0,0.2)",
                    }}
                    itemStyle={{ color: "#20d6a0" }}
                    labelStyle={{ color: "#c5d5ef" }}
                  />

                  <Area
                    type="monotone"
                    dataKey="revenue"
                    name="Revenue"
                    stroke="#20d6a0"
                    strokeWidth={3}
                    fill="url(#revenueGradient)"
                    activeDot={{
                      r: 6,
                      stroke: "#ffffff",
                      strokeWidth: 2,
                      fill: "#20d6a0",
                    }}
                    dot={false}
                    animationDuration={900}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}

          <p className="events-footnote">
            Hover over the chart to inspect daily revenue. The chart
            uses the selected period and data returned by the backend.
          </p>
        </div>
      </div>

      {/* Selected metric details */}
      {selectedDetails && (
        <div
          className="panel metric-details-panel"
          ref={detailsRef}
        >
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
              aria-label="Close metric details"
            >
              ✕ Close
            </button>
          </div>

          {selectedDetails.type === "revenue" ? (
            <div className="revenue-details">
              <div className="stat-row">
                <span>Total Revenue</span>
                <strong>
                  {formatCurrency(overview?.totalRevenue)}
                </strong>
              </div>

              <p className="panel-subtitle">
                This total comes from the backend overview API.
                It represents successful payments for non-cancelled
                orders.
              </p>
            </div>
          ) : selectedDetails.type === "products" ? (
            <div className="products-details">
              <div className="products-summary">
                <div>
                  <span>Total products in database</span>
                  <strong>
                    {Number(overview?.totalProducts ?? products.length)}
                  </strong>
                </div>

                <div>
                  <span>Products returned by API</span>
                  <strong>{products.length}</strong>
                </div>

                <div>
                  <span>Matching products</span>
                  <strong>{filteredProducts.length}</strong>
                </div>
              </div>

              <div className="products-toolbar">
                <input
                  type="search"
                  className="product-search"
                  placeholder="Search by product name, SKU, category or ID..."
                  value={productSearch}
                  onChange={(event) =>
                    setProductSearch(event.target.value)
                  }
                  aria-label="Search products"
                />

                <select
                  className="product-category-filter"
                  value={productCategory}
                  onChange={(event) =>
                    setProductCategory(event.target.value)
                  }
                  aria-label="Filter products by category"
                >
                  {productCategories.map((category) => (
                    <option key={category} value={category}>
                      {category === "All"
                        ? "All categories"
                        : category}
                    </option>
                  ))}
                </select>

                <button
                  type="button"
                  className="retry-button"
                  onClick={() => {
                    setProductSearch("");
                    setProductCategory("All");
                  }}
                >
                  Reset filters
                </button>

                <button
                  type="button"
                  className="retry-button"
                  disabled={productsLoading}
                  onClick={() => setProductRetry((value) => value + 1)}
                >
                  {productsLoading ? "Loading..." : "↻ Reload products"}
                </button>
              </div>

              {productsLoading ? (
                <div className="chart-empty">
                  Loading MAISON products...
                </div>
              ) : productsError ? (
                <div className="error-banner">
                  <p>{productsError}</p>

                  <button
                    type="button"
                    className="retry-button"
                    onClick={() => setProductRetry((value) => value + 1)}
                  >
                    Retry loading products
                  </button>
                </div>
              ) : filteredProducts.length === 0 ? (
                <div className="chart-empty">
                  {products.length === 0
                    ? "No products were returned by the products API."
                    : "No products match your search or category filter."}
                </div>
              ) : (
                <div className="table-wrapper">
                  <table className="details-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>PRODUCT</th>
                        <th>CATEGORY</th>
                        <th>SKU</th>
                        <th>PRICE</th>
                        <th>STATUS</th>
                      </tr>
                    </thead>

                    <tbody>
                      {filteredProducts.map((product, index) => {
                        const id =
                          product.id ??
                          product.productId ??
                          product.product_id ??
                          index;

                        const name = getProductName(product);
                        const category = getProductCategory(product);
                        const sku = getProductSku(product);
                        const price = getProductPrice(product);
                        const active = getProductStatus(product);

                        return (
                          <tr key={id}>
                            <td>{id}</td>
                            <td>{name}</td>
                            <td>{category}</td>
                            <td>{sku}</td>
                            <td>
                              {price == null
                                ? "—"
                                : formatCurrency(price)}
                            </td>
                            <td>
                              {active === true ||
                              active === "true" ? (
                                <span className="product-status-active">
                                  Active
                                </span>
                              ) : active === false ||
                                active === "false" ? (
                                <span className="product-status-inactive">
                                  Inactive
                                </span>
                              ) : (
                                "—"
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}

              <p className="events-footnote">
                Showing {filteredProducts.length} of {products.length} products
                returned by the MAISON Store API.
              </p>
            </div>
          ) : (
            <>
              {selectedMetric === "totalUsers" && usersError && (
                <p className="error-banner">{usersError}</p>
              )}

              <p className="details-count">
                {selectedDetails.records.length} records in this view
              </p>

              {selectedDetails.records.length === 0 ? (
                <p className="empty-details">
                  {selectedMetric === "totalUsers"
                    ? usersError ||
                      "No registered users were returned by the API."
                    : "No matching records found in the latest API response."}
                </p>
              ) : selectedDetails.type === "registeredUsers" ? (
                <div className="table-wrapper">
                  <table className="details-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>NAME</th>
                        <th>EMAIL</th>
                        <th>ROLE</th>
                        <th>STATUS</th>
                        <th>REGISTERED</th>
                      </tr>
                    </thead>

                    <tbody>
                      {selectedDetails.records.map((user, index) => (
                        <tr key={user.id ?? `user-${index}`}>
                          <td>{user.id ?? "—"}</td>
                          <td>
                            {user.fullName ||
                              user.full_name ||
                              user.name ||
                              "Unknown"}
                          </td>
                          <td>{user.email || "—"}</td>
                          <td>{user.role || "—"}</td>
                          <td>
                            {user.active === true
                              ? "Active"
                              : user.active === false
                                ? "Inactive"
                                : "—"}
                          </td>
                          <td>
                            {formatDateTime(
                              user.createdAt || user.created_at
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : selectedDetails.type === "activityUsers" ? (
                <div className="table-wrapper">
                  <table className="details-table">
                    <thead>
                      <tr>
                        <th>USER</th>
                        <th>LAST EVENT</th>
                        <th>PAGE URL</th>
                        <th>TIME</th>
                      </tr>
                    </thead>

                    <tbody>
                      {selectedDetails.records.map((user, index) => (
                        <tr key={`${user.name}-${index}`}>
                          <td>{user.name}</td>
                          <td>{user.lastEvent}</td>
                          <td>{user.lastPage}</td>
                          <td>{formatDateTime(user.time)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <RecentEvents events={selectedDetails.records} />
              )}
            </>
          )}
        </div>
      )}

      {/* Recent events and platform overview */}
      <div className="content-grid">
        <div className="panel events-panel">
          <div className="panel-heading">
            <div>
              <h3>Recent Events</h3>
              <p>Latest activity recorded by your platform</p>
            </div>

            <span className="event-count">
              {events.length} events
            </span>
          </div>

          <RecentEvents events={events} />
        </div>

        <div className="panel activity-panel">
          <h3>Platform Overview</h3>

          <p className="panel-subtitle">
            Current database statistics
          </p>

          <div className="stat-row">
            <span>Active users</span>
            <strong>{overview?.activeUsers ?? 0}</strong>
          </div>

          <div className="stat-row">
            <span>Total users</span>
            <strong>
              {overview?.totalUsers ?? registeredUsers.length}
            </strong>
          </div>

          <div className="stat-row">
            <span>Total products</span>
            <strong>{overview?.totalProducts ?? 0}</strong>
          </div>

          <div className="stat-row">
            <span>Active sessions</span>
            <strong>{overview?.activeSessions ?? 0}</strong>
          </div>

          <div className="stat-row">
            <span>Page views</span>
            <strong>{overview?.pageViews ?? 0}</strong>
          </div>

          <div className="stat-row">
            <span>Total events</span>
            <strong>{overview?.totalEvents ?? 0}</strong>
          </div>

          <div className="stat-row">
            <span>Total Revenue</span>
            <strong>
              {formatCurrency(overview?.totalRevenue)}
            </strong>
          </div>

          <div className="database-status">
            <span className="live-dot" />
            Data loaded from backend
          </div>
        </div>
      </div>
    </section>
  );
}

export default Dashboard;
