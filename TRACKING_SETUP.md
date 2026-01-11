# Live Delivery Tracking System - Setup Guide

## Overview
This system provides real-time location tracking of delivery partners using industry-standard technologies:
- **Google Maps SDK** for map visualization
- **Supabase Realtime** for live location updates
- **Polling fallback** for reliability

## Prerequisites

### 1. Google Maps API Key
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable "Maps SDK for Android"
4. Create an API key
5. Update `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_GOOGLE_MAPS_API_KEY" />
   ```

### 2. Supabase Database Tables

You need to create the following tables in your Supabase database:

#### `delivery_partners` table
```sql
CREATE TABLE delivery_partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id),
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    vehicle_type TEXT DEFAULT 'BIKE',
    is_available BOOLEAN DEFAULT true,
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    last_updated TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Enable Realtime for this table
ALTER PUBLICATION supabase_realtime ADD TABLE delivery_partners;
```

#### `delivery_assignments` table
```sql
CREATE TABLE delivery_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    delivery_partner_id UUID NOT NULL REFERENCES delivery_partners(id),
    assigned_at TIMESTAMP DEFAULT NOW(),
    estimated_arrival TIMESTAMP,
    delivery_status TEXT DEFAULT 'ASSIGNED',
    pickup_latitude DOUBLE PRECISION,
    pickup_longitude DOUBLE PRECISION,
    delivery_latitude DOUBLE PRECISION,
    delivery_longitude DOUBLE PRECISION
);
```

#### Update `orders` table
```sql
ALTER TABLE orders 
ADD COLUMN delivery_partner_id UUID REFERENCES delivery_partners(id),
ADD COLUMN delivery_assignment_id UUID REFERENCES delivery_assignments(id),
ADD COLUMN estimated_delivery_time TIMESTAMP;
```

## How It Works

### 1. Order Flow
1. Order is placed and payment is successful
2. Admin assigns a delivery partner to the order
3. Delivery partner's location is tracked via their app (separate delivery partner app)
4. Customer can view live tracking in the order details screen

### 2. Real-time Updates
- **Primary**: Supabase Realtime listens to `delivery_partners` table changes
- **Fallback**: Polls location every 5 seconds if Realtime is unavailable
- Updates are streamed to the `TrackingViewModel` via Kotlin Flow

### 3. Location Updates
Delivery partners need to update their location in the database. This can be done via:
- A separate delivery partner Android app
- Admin dashboard
- Backend service that receives location updates

Example update query:
```sql
UPDATE delivery_partners 
SET 
    current_latitude = 28.7041,
    current_longitude = 77.1025,
    last_updated = NOW()
WHERE id = 'partner-id';
```

## Usage

### For Customers
1. Go to Profile → My Orders
2. Select an active order
3. If delivery partner is assigned, click "Track Your Order"
4. View live map with delivery partner location and ETA

### Navigation
The tracking screen is accessible via:
- Route: `tracking/{orderId}`
- From OrderDetailsScreen when delivery partner is assigned

## Features

✅ Real-time location tracking
✅ Google Maps integration
✅ ETA calculation
✅ Delivery status tracking
✅ Automatic camera positioning
✅ Route visualization (straight line - can be enhanced with Directions API)
✅ Fallback polling mechanism

## Future Enhancements

1. **Google Directions API**: Replace straight line with actual route
2. **Push Notifications**: Notify customer when delivery partner is nearby
3. **Delivery Partner App**: Separate app for delivery partners to update location
4. **Geofencing**: Automatic status updates when partner reaches destination
5. **Route Optimization**: Optimize delivery routes for multiple orders

## Testing

To test the tracking system:
1. Create a test delivery partner in the database
2. Assign the partner to an order
3. Update the partner's location periodically
4. Open the tracking screen for that order
5. Verify location updates appear on the map

## Troubleshooting

### Maps not showing
- Check Google Maps API key is correctly set
- Verify API key has Maps SDK for Android enabled
- Check API key restrictions

### No location updates
- Verify Supabase Realtime is enabled for `delivery_partners` table
- Check delivery partner has valid coordinates
- Verify delivery assignment exists for the order
- Check network connectivity

### ETA not calculating
- Ensure delivery assignment has destination coordinates
- Verify delivery partner has current location

