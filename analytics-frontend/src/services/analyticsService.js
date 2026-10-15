
const API_BASE_URL = "http://localhost:8080/api/analytics";
const PRODUCT_API_URL = "http://localhost:8080/api/products";

function getAuthHeaders() {
  const token = localStorage.getItem("token");

  if (!token) {
    throw new Error("Please log in to the MAISON Store first.");
  }

  return {
    Authorization: `Bearer ${token}`,
    Accept: "application/json",
  };
}

async function fetchAnalytics(endpoint) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    method: "GET",
    headers: getAuthHeaders(),
  });

  if (response.status === 401) {
    throw new Error(
      "Authentication failed. Check your JWT secret and token validity."
    );
  }

  if (response.status === 403) {
    throw new Error(
      "Access denied. An administrator account is required."
    );
  }

  if (!response.ok) {
    throw new Error(`Analytics API error: ${response.status}`);
  }

  return response.json();
}

export function getOverview() {
  return fetchAnalytics("/overview");
}

export function getRecentEvents() {
  return fetchAnalytics("/events");
}

export async function getUsers() {
  const users = await fetchAnalytics("/users");

  if (!Array.isArray(users)) {
    console.error("Unexpected users API response:", users);
    throw new Error(
      "Users API response is not a list. Check the backend response format."
    );
  }

  return users;
}

export function getRevenueTrend(days = 7) {
  const safeDays = Number(days) === 30 ? 30 : 7;
  return fetchAnalytics(`/revenue-trend?days=${safeDays}`);
}

// MAISON Store products
export async function getProducts() {
  const response = await fetch(`${PRODUCT_API_URL}/admin/all`, {
    method: "GET",
    headers: getAuthHeaders(),
  });

  if (response.status === 401) {
    throw new Error("Your session has expired. Please log in again.");
  }

  if (response.status === 403) {
    throw new Error(
      "Access denied. Log in using a MAISON administrator account."
    );
  }

  if (!response.ok) {
    throw new Error(`Products API error: ${response.status}`);
  }

  const data = await response.json();

  // Support a direct array or common paginated/wrapped responses.
  const products = Array.isArray(data)
    ? data
    : Array.isArray(data?.content)
      ? data.content
      : Array.isArray(data?.products)
        ? data.products
        : Array.isArray(data?.data)
          ? data.data
          : null;

  if (!products) {
    console.error("Unexpected products API response:", data);
    throw new Error(
      "Products API response is not a list. Check the backend response format."
    );
  }

  return products;
}
