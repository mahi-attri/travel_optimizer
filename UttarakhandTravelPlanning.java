import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.*;


public class UttarakhandTravelPlanning extends JPanel {
    private Map<String, TouristSpot> spotsMap;
    private java.util.List<TouristConnection> allConnections; 
    private java.util.List<TouristSpot> currentPath; 

    // UI Components
    private JComboBox<String> startComboBox;
    private JComboBox<String> endComboBox;
    private JTextArea routeDetailsArea;

    // Map visualization parameters
    private double zoomFactor = 1.0;
    private int panX = 0, panY = 0;
    private Point lastDragPoint;

    // Geographical bounds
    private double minLat, maxLat, minLon, maxLon;

    public UttarakhandTravelPlanning() {
        spotsMap = new HashMap<>();
        allConnections = new ArrayList<>();
        currentPath = new ArrayList<>();

        initializeMap();
        setupUI();
    }

    private void initializeMap() {
        loadTouristSpotsFromDatabase();
        loadConnectionsFromDatabase();
        calculateGeographicalBounds();
        setupInteractiveMapFeatures();
    }

    private void loadTouristSpotsFromDatabase() {
        String url = "jdbc:mysql://localhost:3306/minor"; // Update with your database URL
        String user = "root"; // Update with your database username
        String password = "user"; // Update with your database password

        String query = "SELECT name, latitude, longitude FROM TouristSpots";
        try (java.sql.Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query);
             BufferedWriter writer = new BufferedWriter(new FileWriter("loc.csv"))) {

            writer.write("Name,Latitude,Longitude\n"); // CSV Header
            while (rs.next()) {
                String name = rs.getString("name");
                double latitude = rs.getDouble("latitude");
                double longitude = rs.getDouble("longitude");
                spotsMap.put(name, new TouristSpot(name, latitude, longitude));
                writer.write(String.format("%s,%f,%f\n", name, latitude, longitude));
            }
        } catch (SQLException | IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading locations from database: " + e.getMessage(), 
                "Loading Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadConnectionsFromDatabase() {
        String url = "jdbc:mysql://localhost:3306/minor"; // Update with your database URL
        String user = "root"; // Update with your database username
        String password = "user"; // Update with your database password
    
        String query = "SELECT ts1.name AS source_name, ts2.name AS target_name, c.distance " +
                       "FROM Connections c " +
                       "JOIN TouristSpots ts1 ON c.source_id = ts1.id " +
                       "JOIN TouristSpots ts2 ON c.target_id = ts2.id";
    
        try (java.sql.Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query);
             BufferedWriter writer = new BufferedWriter(new FileWriter("con.csv"))) {
    
            writer.write("Source,Target,Distance\n"); // CSV Header
            while (rs.next()) {
                String sourceName = rs.getString("source_name");
                String targetName = rs.getString("target_name");
                double distance = rs.getDouble("distance");
    
                TouristSpot source = spotsMap.get(sourceName);
                TouristSpot target = spotsMap.get(targetName);
                
                if (source != null && target != null) {
                    allConnections.add(new TouristConnection(source, target, distance));
                    writer.write(String.format("%s,%s,%f\n", sourceName, targetName, distance));
                }
            }
        } catch (SQLException | IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading connections from database: " + e.getMessage(), 
                "Loading Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void calculateGeographicalBounds() {
        minLat = spotsMap.values().stream()
            .mapToDouble(TouristSpot::getLatitude).min().orElse(0);
        maxLat = spotsMap.values().stream()
            .mapToDouble(TouristSpot::getLatitude).max().orElse(0);
        minLon = spotsMap.values().stream()
            . mapToDouble(TouristSpot::getLongitude).min().orElse(0);
        maxLon = spotsMap.values().stream()
            .mapToDouble(TouristSpot::getLongitude).max().orElse(0);
    }

    private void setupInteractiveMapFeatures() {
        setFocusable(true);
        addMouseWheelListener(e -> {
            int mouseX = e.getX();
            int mouseY = e.getY();
            double scaleFactor = e.getPreciseWheelRotation() < 0 ? 1.1 : 0.9;
            double newZoomFactor = Math.max(0.5, Math.min(zoomFactor * scaleFactor, 5.0));

            if (Math.abs(newZoomFactor - zoomFactor) > 0.01) {
                double oldZoomFactor = zoomFactor;
                zoomFactor = newZoomFactor;
                double zoomRatio = zoomFactor / oldZoomFactor;
                panX = (int) (panX - (mouseX - panX) * (1 - zoomRatio));
                panY = (int) (panY - (mouseY - panY) * (1 - zoomRatio));
            }

            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastDragPoint = e.getPoint();
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                panX += e.getX() - lastDragPoint.x;
                panY += e.getY() - lastDragPoint.y;
                lastDragPoint = e.getPoint();
                repaint();
            }
        });
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        
        JPanel dropdownPanel = new JPanel(new FlowLayout());
        startComboBox = new JComboBox<>(spotsMap.keySet().toArray(new String[0]));
        endComboBox = new JComboBox<>(spotsMap.keySet().toArray(new String[0]));

        JButton findBestPathButton = new JButton("Find Best Path");
        findBestPathButton.addActionListener(e -> findAndDisplayBestPath());

        JButton findAllPathsButton = new JButton("Find All Paths");
        findAllPathsButton.addActionListener(e -> findAndDisplayAllPaths());

        dropdownPanel.add(new JLabel("Start: "));
        dropdownPanel.add(startComboBox);
        dropdownPanel.add(new JLabel("End: "));
        dropdownPanel.add(endComboBox);
        dropdownPanel.add(findBestPathButton);
        dropdownPanel.add(findAllPathsButton);  

        routeDetailsArea = new JTextArea(10, 30);
        routeDetailsArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(routeDetailsArea);

        add(dropdownPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.EAST);
    }

    private void findAndDisplayBestPath() {
        String startName = (String) startComboBox.getSelectedItem();
        String endName = (String) endComboBox.getSelectedItem();

        TouristSpot start = spotsMap.get(startName);
        TouristSpot end = spotsMap.get(endName);

        if (start == null || end == null) {
            JOptionPane.showMessageDialog(this, 
                    "Please select valid start and end locations", 
                    "Route Error", 
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        java.util.List<java.util.List<TouristSpot>> allPaths = findAllPaths(start, end);
        java.util.List<TouristSpot> bestPath = findBestPath(allPaths);

        displayRouteDetails(bestPath);
        currentPath = bestPath;
        repaint();
    }

    private void findAndDisplayAllPaths() {
        String startName = (String) startComboBox.getSelectedItem();
        String endName = (String) endComboBox.getSelectedItem();

        TouristSpot start = spotsMap.get(startName);
        TouristSpot end = spotsMap.get(endName);

        if (start == null || end == null) {
            JOptionPane.showMessageDialog(this, 
                    "Please select valid start and end locations", 
                    "Route Error", 
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<List<TouristSpot>> allPaths = findAllPaths(start, end);
        displayAllRouteDetails(allPaths);
        repaint();
    }

    private List<List<TouristSpot>> findAllPaths(TouristSpot start, TouristSpot end) {
        Map<TouristSpot, List<TouristSpot>> graph = buildGraph();
        List<List<TouristSpot>> allPaths = new ArrayList<>();
        Set<TouristSpot> visited = new HashSet<>();
        List<TouristSpot> currentPath = new ArrayList<>();
        
        dfs(start, end, visited, currentPath, allPaths, graph);
        return allPaths;
    }
    
    private void dfs(TouristSpot current, TouristSpot end, Set<TouristSpot> visited, 
                     List<TouristSpot> currentPath, List<List<TouristSpot>> allPaths, 
                     Map<TouristSpot, List<TouristSpot>> graph) {
        visited.add(current);
        currentPath.add(current);
    
        if (current.equals(end)) {
            allPaths.add(new ArrayList<>(currentPath)); // Found a path
        } else {
            for (TouristSpot neighbor : graph.get(current)) {
                if (!visited.contains(neighbor)) {
                    dfs(neighbor, end, visited, currentPath, allPaths, graph);
                }
            }
        }
    
        // Backtrack
        visited.remove(current);
        currentPath.remove(currentPath.size() - 1);
    }
    
    private Map<TouristSpot, List<TouristSpot>> buildGraph() {
        Map<TouristSpot, List<TouristSpot>> graph = new HashMap<>();
        for (TouristConnection conn : allConnections) {
            graph.putIfAbsent(conn.getStart(), new ArrayList<>());
            graph.putIfAbsent(conn.getEnd(), new ArrayList<>());
            graph.get(conn.getStart()).add(conn.getEnd());
            graph.get(conn.getEnd()).add(conn.getStart()); // Add reverse connection for bidirectionality
        }
        return graph;
    }

    private void displayRouteDetails(List<TouristSpot> route) {
        if (route.isEmpty()) {
            routeDetailsArea.setText("No route found.");
            return;
        }
    
        StringBuilder details = new StringBuilder("Best Route Details:\n\n");
        double totalDistance = 0;
    
        for (int i = 0; i < route.size() - 1; i++) {
            TouristSpot start = route.get(i);
            TouristSpot end = route.get(i + 1);
            
            TouristConnection connection = findConnection(start, end);
            if (connection != null) {
                details.append(String.format("%d. %s → %s (%.2f km)\n", 
                    i + 1, start.getName(), end.getName(), connection.getDistance()));
                totalDistance += connection.getDistance();
            } else {
                details.append(String.format("%d. %s → %s (Distance not found)\n", 
                    i + 1, start.getName(), end.getName()));
            }
        }
    
        details.append(String.format("\nTotal Route Distance: %.2f km", totalDistance));
        routeDetailsArea.setText(details.toString());
    }

    
    private TouristConnection findConnection(TouristSpot start, TouristSpot end) {
        return allConnections.stream()
            .filter(c -> (c.getStart().equals(start) && c.getEnd().equals(end)) ||
                          (c.getStart().equals(end) && c.getEnd().equals(start))) // Check both directions
            .findFirst()
            .orElse(null);
    }

    private void displayAllRouteDetails(List<List<TouristSpot>> allPaths) {
        if (allPaths.isEmpty()) {
            routeDetailsArea.setText("No route found.");
            return;
        }
    
        StringBuilder details = new StringBuilder("All Routes:\n\n");
        for (List<TouristSpot> route : allPaths) {
            double totalDistance = calculateTotalDistance(route);
            details.append("Route: ");
            for (int i = 0; i < route.size() - 1; i++) {
                TouristSpot start = route.get(i);
                TouristSpot end = route.get(i + 1);
                TouristConnection connection = findConnection(start, end);
                if (connection != null) {
                    details.append(String.format("%s → ", start.getName()));
                }
            }
            details.append(String.format("%s (Total Distance: %.2f km)\n", route.get(route.size() - 1).getName(), totalDistance));
        }
    
        routeDetailsArea.setText(details.toString());
    }

    private double calculateTotalDistance(List<TouristSpot> route) {
        double totalDistance = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            TouristSpot start = route.get(i);
            TouristSpot end = route.get(i + 1);
            TouristConnection connection = findConnection(start, end);
            if (connection != null) {
                totalDistance += connection.getDistance();
            }
        }
        return totalDistance;
    }

    private Point scaleToMap(TouristSpot spot) {
        int x = (int) ((spot.getLongitude() - minLon) / (maxLon - minLon) * getWidth() * zoomFactor + panX);
        int y = (int) ((maxLat - spot.getLatitude()) / (maxLat - minLat) * getHeight() * zoomFactor + panY);
        return new Point(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setColor(Color.LIGHT_GRAY);
        for (TouristConnection conn : allConnections) {
            Point start = scaleToMap(conn.getStart());
            Point end = scaleToMap(conn.getEnd());
            g2d.drawLine(start.x, start.y, end.x, end.y);
        }

        g2d.setColor(Color.RED);
        for (TouristSpot spot : spotsMap.values()) {
            Point p = scaleToMap(spot);
            g2d.fillOval(p.x - 5, p.y - 5, 10, 10);
            g2d.drawString(spot.getName(), p.x + 10, p.y);
        }

        if (!currentPath.isEmpty()) {
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(3));
            for (int i = 0; i < currentPath.size() - 1; i++) {
                Point start = scaleToMap(currentPath.get(i));
                Point end = scaleToMap(currentPath.get(i + 1));
                g2d.drawLine(start.x, start.y, end.x, end.y);
            }
        }
    }

    private List<TouristSpot> findBestPath(List<List<TouristSpot>> allPaths) {
        List<TouristSpot> bestPath = null;
        double shortestDistance = Double.MAX_VALUE;
    
        for (List<TouristSpot> path : allPaths) {
            double totalDistance = calculateTotalDistance(path);
            if (totalDistance < shortestDistance) {
                shortestDistance = totalDistance;
                bestPath = path; // Update bestPath if a shorter path is found
            }
        }
    
        return bestPath != null ? bestPath : Collections.emptyList(); // Return an empty list if no path found
    }
    @SuppressWarnings("unused")
    private void collectRatingsAndSaveToCSV(List<TouristSpot> route) {
        Map<String, Integer> ratings = new HashMap<>();
        StringBuilder ratingDetails = new StringBuilder("Ratings for Tourist Spots:\n\n");

        for (TouristSpot spot : route) {
            String ratingInput = JOptionPane.showInputDialog(this, 
                    "Rate the spot " + spot.getName() + " (1 to 5 stars):");
            
            try {
                int rating = Integer.parseInt(ratingInput);
                if (rating >= 1 && rating <= 5) {
                    ratings.put(spot.getName(), rating);
                    ratingDetails.append(spot.getName()).append(": ").append(rating).append(" stars\n");
                } else {
                    JOptionPane.showMessageDialog(this, 
                            "Please enter a rating between 1 and 5.", 
                            "Invalid Rating", 
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, 
                        "Please enter a valid number for the rating.", 
                        "Invalid Input", 
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        JOptionPane.showMessageDialog(this, ratingDetails.toString(), 
                "Tourist Spot Ratings", JOptionPane.INFORMATION_MESSAGE);

        saveRatingsToCSV(ratings);
    }

    private void saveRatingsToCSV(Map<String, Integer> ratings) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("ratings.csv"))) {
            writer.write("Tourist Spot, Rating\n");  // CSV Header

            for (Map.Entry<String, Integer> entry : ratings.entrySet()) {
                writer.write(entry.getKey() + ", " + entry.getValue() + "\n");
            }

            JOptionPane.showMessageDialog(this, "Ratings saved to ratings.csv.", 
                    "Save Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                    "Error saving ratings: " + e.getMessage(), 
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean showLoginScreen() {
        int option = JOptionPane.showOptionDialog(null, 
            "Do you have an account?", 
            "Login", 
            JOptionPane.YES_NO_OPTION, 
            JOptionPane.QUESTION_MESSAGE, 
            null, null, null);
        
        if (option == JOptionPane.YES_OPTION) {
            return loginUser ();
        } else {
            return createAccount();
        }
    }

    private static boolean loginUser () {
        String username = JOptionPane.showInputDialog("Enter username:");
        String password = JOptionPane.showInputDialog("Enter password:");

        return verifyCredentials(username, password);
    }

    private static boolean createAccount() {
        String username = JOptionPane.showInputDialog("Enter a new username:");
        String password = JOptionPane.showInputDialog("Enter a new password:");

        return saveAccount(username, password);
    }

    private static boolean verifyCredentials(String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader("accounts.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
                    return true;  // Credentials match
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                    "Error reading accounts file: " + e.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;  // No match found
    }

    private static boolean saveAccount(String username, String password) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("accounts.txt", true))) {
            writer.write(username + "," + password + "\n");
            return true;  // Account saved successfully
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                    "Error saving account: " + e.getMessage(), 
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            return false;  // Failed to save account
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            if (showLoginScreen()) {
                JFrame frame = new JFrame("Uttarakhand Travel Planner");
                UttarakhandTravelPlanning planner = new UttarakhandTravelPlanning();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(planner);
                frame.setSize(1200, 800);
                frame.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, "Login failed. Exiting application.");
                System.exit(0);
            }
        });
    }
}

class TouristConnection {
    private TouristSpot start;
    private TouristSpot end;
    private double distance;

    public TouristConnection(TouristSpot start, TouristSpot end, double distance) {
        this.start = start;
        this.end = end;
        this.distance = distance;
    }

    public TouristSpot getStart() { return start; }
    public TouristSpot getEnd() { return end; }
    public double getDistance() { return distance; }
}

class TouristSpot {
    private String name;
    private double latitude, longitude;

    public TouristSpot(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TouristSpot that = (TouristSpot) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}