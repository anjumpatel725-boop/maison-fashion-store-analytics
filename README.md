# MAISON — Advanced Real-Time Analytics Platform

**A reactive analytics backend powering the MAISON Fashion Store and its analytics dashboard.**



\


## 🚀 Live Applications

| Component                | URL                                                                                       |
| ------------------------ | ----------------------------------------------------------------------------------------- |
| 🛍️ MAISON Fashion Store | [Open Store](https://maison-fashion-store-one.vercel.app/)                                |
| 📊 Analytics Dashboard   | [Open Analytics Dashboard](https://maison-fashion-store-analytics.vercel.app/)            |
| ⚙️ Backend API           | [Open Deployed Backend](https://maison-fashion-store-analytics.onrender.com)              |
| 💻 Backend Source Code   | [GitHub Repository](https://github.com/anjumpatel725-boop/maison-fashion-store-analytics) |
| 🛒 Store Source Code     | [Store Repository](https://github.com/anjumpatel725-boop/maison-fashion-store)            |

## 📌 Project Overview

The MAISON Advanced Real-Time Analytics Platform is a backend system designed to support the MAISON fashion e-commerce application and its analytics dashboard.

It provides APIs for application functionality, authentication, customer activity tracking, and analytics data retrieval. The dashboard consumes these APIs to display user metrics, session information, page views, and recent events.

The backend uses Spring WebFlux and reactive database access with R2DBC and PostgreSQL.

## ✨ Key Features

- **Reactive backend:** Built with Spring Boot WebFlux.
- **Authentication:** JWT-based authentication for protected operations.
- **Customer activity tracking:** Records supported events from the MAISON Store.
- **Analytics APIs:** Provides overview metrics and recent event data.
- **Session tracking:** Stores and monitors user session activity.
- **E-commerce integration:** Supports the store's product, customer, and order workflows.
- **PostgreSQL database:** Stores application and analytics data.
- **Dashboard integration:** Connects the backend to the React analytics frontend.
- **Cloud deployment:** Backend hosted on Render.

## 🛠️ Technology Stack

| Technology         | Purpose                             |
| ------------------ | ----------------------------------- |
| Java               | Backend programming language        |
| Spring Boot        | Application framework               |
| Spring WebFlux     | Reactive web APIs                   |
| R2DBC              | Reactive relational database access |
| PostgreSQL         | Relational database                 |
| JWT                | Authentication and authorization    |
| Maven              | Dependency management and build     |
| React and Recharts | Analytics dashboard                 |
| Vercel             | Frontend deployment                 |
| Render             | Backend deployment                  |

## 🏗️ System Architecture

```text
          MAISON Fashion Store
            React + Vite
                  |
                  v
          Spring Boot Backend
             WebFlux APIs
                  |
          +-------+--------+
          |                |
          v                v
    Authentication    Activity Tracking
       JWT                 API
          |                |
          +-------+--------+
                  |
                  v
             PostgreSQL
                  |
                  v
           Analytics APIs
                  |
                  v
        React Analytics Dashboard
           + Recharts Charts
```

## 📊 Analytics Pipeline

1. A customer performs an action in the MAISON Store.
2. The frontend sends supported activity events to the backend.
3. The backend validates incoming requests and authenticated user information where required.
4. Activity records are stored in PostgreSQL.
5. Analytics APIs retrieve event and session information and calculate dashboard metrics.
6. The dashboard refreshes its data periodically and displays updated metrics.

## 📈 Supported Activity Events

Examples of event types supported by the application include:

| Event              | Description                           |
| ------------------ | ------------------------------------- |
| `PAGE_VIEW`        | A customer visits a page              |
| `PRODUCT_VIEW`     | A customer views a product            |
| `ADD_TO_CART`      | A customer adds a product to the cart |
| `BUY_NOW`          | A customer initiates a buy-now action |
| `CHECKOUT_STARTED` | Checkout begins                       |
| `ORDER_PLACED`     | An order is placed                    |
| `LOGIN`            | Login activity, where recorded        |
| `LOGOUT`           | Logout activity, where recorded       |

The events actually recorded depend on the frontend tracking calls and the backend implementation.

## 🔌 API Overview

The backend provides APIs for authentication, products, orders, activity tracking, and analytics.

| Endpoint               | Purpose                                                    |
| ---------------------- | ---------------------------------------------------------- |
| `/api/auth/register`   | Customer registration                                      |
| `/api/auth/login`      | Customer login                                             |
| `/api/products`        | Product catalogue                                          |
| `/api/activity/events` | Record supported activity events                           |
| `/api/analytics`       | Analytics API base path                                    |
| `/actuator`            | Application monitoring endpoints, subject to configuration |

Specific endpoints may require authentication. Refer to the controller implementations for the exact HTTP methods, request formats, and response structures.

### Example Activity Request

```http
POST /api/activity/events
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN
```

Example JSON body:

```json
{
  "eventType": "PAGE_VIEW",
  "pageUrl": "/products"
}
```

Use a valid token and a supported event type when testing protected endpoints.

## 🗄️ Database Design

The application uses PostgreSQL for relational storage.

The broader project includes tables for:

- Customer accounts
- Products
- Customer orders
- Customer order items
- Analytics events
- User sessions

The exact schema and relationships are defined by the database migrations or SQL setup used by the project.

## ⚙️ Local Setup

### Prerequisites

Install:

- Java JDK compatible with the project's Maven configuration
- Maven, or use the included Maven Wrapper
- PostgreSQL
- Git
- Node.js and npm if running the frontend projects locally

### 1. Clone the Backend Repository

```bash
git clone https://github.com/anjumpatel725-boop/maison-fashion-store-analytics.git
cd maison-fashion-store-analytics
```

### 2. Configure Environment Variables

Configure the PostgreSQL connection and JWT secret in your local environment or Spring Boot configuration.

Example environment variables:

```text
JWT_SECRET=your_secure_secret
```

Set the database URL, username, and password according to your local PostgreSQL configuration.

**Security:** Never commit production credentials, database passwords, or JWT secrets to GitHub. Use environment variables and your deployment provider's secret settings.

### 3. Start the Backend

If the repository contains the Maven Wrapper, run on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Or, if Maven is installed:

```bash
mvn spring-boot:run
```

The application uses port `8080` in the local development configuration unless configured otherwise.

### 4. Verify the Backend

Start the application and inspect the console for successful startup. Test the relevant endpoints using a browser, Postman, or another API client.

The deployed backend base URL is:

[https://maison-fashion-store-analytics.onrender.com](https://maison-fashion-store-analytics.onrender.com)

### 5. Connect the Frontend

Configure the analytics frontend to use the correct backend API base URL. Ensure production CORS settings allow the deployed frontend origin.

The analytics frontend and store frontend are maintained as separate applications.

## 🔐 Security

- JWT-based authentication for protected APIs.
- Server-side validation of incoming requests.
- Environment-based secret management.
- CORS configuration for permitted frontend origins.
- Role-based authorization for administrative operations where implemented.
- HTTPS for deployed application communication.

Never expose secret keys, private credentials, or real customer information in public source code or screenshots.

## ☁️ Deployment

| Component                    | Hosting    |
| ---------------------------- | ---------- |
| MAISON Store Frontend        | Vercel     |
| Analytics Dashboard Frontend | Vercel     |
| Spring Boot Backend          | Render     |
| Relational Database          | PostgreSQL |

Deployment configuration, environment variables, CORS settings, and database connectivity must be maintained for the applications to work together.

## 🧪 Testing Checklist

- [x] Backend starts successfully.
- [x] PostgreSQL connection works.
- [x] Registration and login APIs respond correctly.
- [x] JWT-protected endpoints validate authentication.
- [x] Product APIs return the expected data.
- [x] Activity events can be recorded.
- [x] Analytics overview and recent events load.
- [x] Dashboard displays backend data.
- [x] Store checkout and order workflows work.
- [x] Production URLs are accessible and configured correctly.

Mark each item complete only after testing it.

## 🔮 Future Enhancements

Potential future improvements include:

- WebSocket or Server-Sent Events for push-based live dashboard updates.
- Machine-learning-based customer behaviour analysis.
- Automated anomaly detection and alerts.
- Advanced search and analytics filtering.
- Performance monitoring and distributed tracing.
- Automated integration tests and CI/CD pipelines.

These are proposed enhancements unless already implemented in the current codebase.

## 👨‍💻 Author

**Anjum Patel**

GitHub: [anjumpatel725-boop](https://github.com/anjumpatel725-boop)

## 📄 Project Purpose

This project demonstrates reactive backend development, REST API design, JWT authentication, PostgreSQL integration, customer activity tracking, and analytics visualization for a fashion e-commerce application.

*Built as part of the MAISON Fashion Store and Advanced Real-Time Analytics Dashboard project.*
