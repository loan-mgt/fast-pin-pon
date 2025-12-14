# Build stage
FROM golang:1.24-alpine AS builder

# Set working directory
WORKDIR /app

# Copy go mod files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

# Final stage
FROM alpine:3.20

# Install ca-certificates for HTTPS requests
RUN apk --no-cache add ca-certificates && \
    addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup && \
    mkdir -p /var/log/fast-pin-pon && \
    chmod 777 -R /var/log && \
    chown -R appuser:appgroup /var/log/fast-pin-pon

# Set working directory
WORKDIR /app

# Copy the binary and migrations from builder stage
COPY --from=builder /app/main .
COPY --from=builder /app/migrations ./migrations

# Switch to non-root user
USER appuser

# Run the application
CMD ["./main"]