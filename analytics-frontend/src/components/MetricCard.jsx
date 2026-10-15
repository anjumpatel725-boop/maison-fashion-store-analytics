function MetricCard({
  title,
  value,
  icon,
  description,
  color,
  onClick,
  selected = false,
}) {
  const displayValue =
    typeof value === "number"
      ? value.toLocaleString("en-IN")
      : value ?? "0";

  const isClickable = typeof onClick === "function";

  return (
    <button
      type="button"
      className={`metric-card ${
        isClickable ? "metric-card-clickable" : ""
      } ${selected ? "metric-card-selected" : ""}`}
      onClick={isClickable ? onClick : undefined}
      aria-pressed={isClickable ? selected : undefined}
      title={
        isClickable
          ? `View details for ${title}`
          : title
      }
      disabled={!isClickable}
    >
      <div className="metric-top">
        <span className={`metric-icon ${color || ""}`}>
          {icon}
        </span>

        <span className="metric-indicator">
          {isClickable ? (selected ? "↗" : "↘") : ""}
        </span>
      </div>

      <p className="metric-title">{title}</p>

      <h2 className="metric-value">{displayValue}</h2>

      <p className="metric-description">{description}</p>

      {isClickable && (
        <span className="metric-click-hint">
          {selected
            ? "Viewing details ↑"
            : "Click to view details ↓"}
        </span>
      )}
    </button>
  );
}

export default MetricCard;
