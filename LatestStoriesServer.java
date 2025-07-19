import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import com.sun.net.httpserver.*;

public class LatestStoriesServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/getTimeStories", new LatestStoriesHandler());
        server.setExecutor(null);
        System.out.println("Server started at http://localhost:" + port + "/getTimeStories");
        server.start();
    }

    static class LatestStoriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }

            List<Map<String, String>> stories = getLatestStories();
            String json = toJson(stories);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(json.getBytes());
            os.close();
        }
    }

    private static List<Map<String, String>> getLatestStories() {
        List<Map<String, String>> result = new ArrayList<>();
        String html = fetchHomePage();
        if (html == null) return result;

        // Find all <a href="...">...</a>
        Pattern p = Pattern.compile("<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);

        int count = 0;
        Set<String> seen = new HashSet<>();
        while (m.find() && count < 6) {
            String href = m.group(1);
            String title = m.group(2).replaceAll("<[^>]+>", "").replaceAll("&amp;", "&").trim();

            // Only pick links that look like stories (contain a number and are not repeated)
            if (href.matches("/\\d{7,}.*") && !seen.contains(href) && !title.isEmpty()) {
                seen.add(href);
                Map<String, String> story = new HashMap<>();
                story.put("title", title);
                story.put("link", "https://time.com" + href);
                result.add(story);
                count++;
            }
        }
        return result;
    }

    private static String fetchHomePage() {
        StringBuilder sb = new StringBuilder();
        try {
            URL url = new URL("https://time.com");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                sb.append(inputLine).append("\n");
            }
            in.close();
        } catch (Exception e) {
            System.out.println("Error fetching homepage: " + e.getMessage());
            return null;
        }
        System.out.println(sb.toString());
        return sb.toString();
    }

    // Simple JSON serialization (no external libraries)
    private static String toJson(List<Map<String, String>> stories) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < stories.size(); i++) {
            Map<String, String> story = stories.get(i);
            sb.append("  {\"title\": \"").append(escapeJson(story.get("title")))
                    .append("\", \"link\": \"").append(escapeJson(story.get("link"))).append("\"}");
            if (i < stories.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}