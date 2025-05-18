import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * VetClinicChatbot_2 - a desktop chatbot app for veterinary clinic services using Java Swing.
 * Features:
 * - User login with chat history persistence
 * - Remind pet owners of appointments with predefined data
 * - Give emotional support messages
 * - Answer queries related to pet adoption events and post-visit care
 * - Uses OpenRouter API for smart replies
 */
public class VetClinicChatbot_2 extends JFrame {
    private static final long serialVersionUID = 1L;

    private JTextPane chatPane;
    private JTextField inputField;
    private JButton sendButton;
    private JButton logoutButton;
    private JLabel userLabel;
    private StyledDocument doc;
    private SimpleAttributeSet userStyle, botStyle, infoStyle;

    // Predefined Q&A
    private Map<String, String> predefinedQA = new HashMap<>();

    // Appointment sample reminder data (simple)
    private java.util.List<String> appointmentReminders = new ArrayList<>();

    // Your OpenRouter API key here - user must paste their key here
    private String OPENROUTER_API_KEY = "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"; //<-- Set your OpenRouter API key here

    // Current logged in username
    private String currentUser = null;

    // Chat history list of messages (with who spoke)
    private java.util.List<Message> chatHistory = new ArrayList<>();

    // File folder to save user data
    private final File userDataDir = new File("user_data");

    public VetClinicChatbot_2() {
        super("Chatbot");
        setSize(400, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        populatePredefinedQA();
        populateAppointmentReminders();

        // Ensure user data directory exists
        if (!userDataDir.exists()) {
            userDataDir.mkdir();
        }

        promptLogin();
    }

    private void initComponents() {
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        doc = chatPane.getStyledDocument();
        JScrollPane scrollPane = new JScrollPane(chatPane);

        inputField = new JTextField();
        sendButton = new JButton("Send");
        logoutButton = new JButton("Logout");
        userLabel = new JLabel("Not logged in");

        userStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(userStyle, new Color(0, 102, 204));
        StyleConstants.setBold(userStyle, true);

        botStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(botStyle, new Color(0, 153, 0));
        StyleConstants.setBold(botStyle, false);

        infoStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(infoStyle, Color.DARK_GRAY);
        StyleConstants.setItalic(infoStyle, true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(userLabel, BorderLayout.WEST);
        topPanel.add(logoutButton, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        panel.add(inputPanel, BorderLayout.SOUTH);

        add(panel);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        logoutButton.addActionListener(e -> logout());

        // Disable input and send button until login
        setInputEnabled(false);
    }

    private void promptLogin() {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(this, "Enter your username:", "Login", JOptionPane.QUESTION_MESSAGE);
            if (username == null || username.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username is required to use the chatbot. Exiting.", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } else {
                currentUser = username.trim();
                userLabel.setText("Logged in as: " + currentUser);
                loadUserChatHistory();
                appendBotMessage("Welcome back, " + currentUser + "! Ask me about appointments, pet adoption events, post-visit care or chat for emotional support.");
                setInputEnabled(true);
            }
        });
    }

    private void logout() {
        saveUserChatHistory();
        chatHistory.clear();
        clearChatPane();
        currentUser = null;
        userLabel.setText("Not logged in");
        setInputEnabled(false);
        JOptionPane.showMessageDialog(this, "You have been logged out.");
        promptLogin();
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void clearChatPane() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void populatePredefinedQA() {
        predefinedQA.put("what are the adoption events", "Our next pet adoption event is on Saturday at 10 AM in the main clinic lobby. Everyone is welcome!");
        predefinedQA.put("how to take care after visit", "Post-visit care includes administering prescribed medications on time, monitoring your pet's behavior, keeping the wound clean if any, and scheduling follow-ups if necessary.");
        predefinedQA.put("i am sad about my pet", "I'm here for you. Remember, your pet loves you very much. If you want to talk or share, I'm listening.");
        predefinedQA.put("appointment reminder", "You have an upcoming appointment. Please provide your pet's name to get details.");
        predefinedQA.put("help", "You can ask me about appointment reminders, pet adoption events, post-visit care, or chat for emotional support.");
    }

    private void populateAppointmentReminders() {
        appointmentReminders.add("Buddy: Appointment on 2024-07-10 at 2:00 PM with Dr. Smith.");
        appointmentReminders.add("Mittens: Appointment on 2024-07-12 at 11:00 AM with Dr. Johnson.");
        appointmentReminders.add("Charlie: Appointment on 2024-07-15 at 9:30 AM with Dr. Lee.");
    }

    private void sendMessage() {
        String userText = inputField.getText().trim();
        if (userText.isEmpty() || currentUser == null) {
            return;
        }
        appendUserMessage(userText);
        inputField.setText("");

        // Process input asynchronously to avoid UI freezing
        new Thread(() -> processUserInput(userText)).start();
    }

    private void processUserInput(String input) {
        String lowerInput = input.toLowerCase();

        // Check for appointment reminders request with pet name
        if (lowerInput.contains("appointment reminder")) {
            String petName = extractPetName(lowerInput);
            if (petName != null) {
                String reminder = getAppointmentReminderForPet(petName);
                if (reminder != null) {
                    appendBotMessage(reminder);
                    return;
                } else {
                    appendBotMessage("Sorry, I couldn't find an appointment reminder for " + petName + ".");
                    return;
                }
            } else {
                appendBotMessage("Please provide the name of your pet to get appointment reminder.");
                return;
            }
        }

        for (String key : predefinedQA.keySet()) {
            if (lowerInput.contains(key)) {
                appendBotMessage(predefinedQA.get(key));
                return;
            }
        }

        if (lowerInput.contains("sad") || lowerInput.contains("lonely") || lowerInput.contains("miss my pet")
                || lowerInput.contains("lost my pet")) {
            appendBotMessage("I'm sorry you are feeling that way. Remember, it's okay to feel sad. If you want to talk more, I'm here.");
            return;
        }

        String response = callOpenRouterAPI(input);
        if (response != null) {
            appendBotMessage(response);
        } else {
            appendBotMessage("Sorry, I couldn't get a response at this time. Please try again later.");
        }
    }

    private String extractPetName(String input) {
        for (String reminder : appointmentReminders) {
            String petName = reminder.split(":")[0].toLowerCase();
            if (input.contains(petName)) {
                return petName.substring(0, 1).toUpperCase() + petName.substring(1);
            }
        }
        return null;
    }

    private String getAppointmentReminderForPet(String petName) {
        for (String reminder : appointmentReminders) {
            if (reminder.toLowerCase().startsWith(petName.toLowerCase())) {
                return "Appointment reminder for " + petName + ": " + reminder.substring(reminder.indexOf(":") + 1).trim();
            }
        }
        return null;
    }

    private void appendUserMessage(String message) {
        chatHistory.add(new Message("user", message));
        appendMessage("You: " + message + "\n", userStyle);
    }

    private void appendBotMessage(String message) {
        chatHistory.add(new Message("bot", message));
        appendMessage("VetBot: " + message + "\n", botStyle);
    }

    private void appendMessage(String msg, AttributeSet style) {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.insertString(doc.getLength(), msg, style);
                chatPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private String callOpenRouterAPI(String prompt) {
        if (OPENROUTER_API_KEY == null || OPENROUTER_API_KEY.trim().isEmpty()) {
            return "OpenRouter API key not set. Please set your API key in the code.";
        }

        String apiUrl = "https://openrouter.ai/api/v1/chat/completions";

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + OPENROUTER_API_KEY.trim());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject root = new JSONObject();
            root.put("model", "gpt-4o-mini");

            JSONArray messagesArray = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messagesArray.put(userMessage);

            root.put("messages", messagesArray);

            byte[] out = root.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream stream = conn.getOutputStream();
            stream.write(out);

            int status = conn.getResponseCode();
            if (status != 200) {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    BufferedReader brErr = new BufferedReader(new InputStreamReader(errorStream));
                    String line;
                    StringBuilder errResponse = new StringBuilder();
                    while ((line = brErr.readLine()) != null) {
                        errResponse.append(line);
                    }
                    return "OpenRouter API error: " + errResponse.toString();
                } else {
                    return "OpenRouter API error: HTTP status " + status;
                }
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder responseStrBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                responseStrBuilder.append(line);
            }

            JSONObject responseJson = new JSONObject(responseStrBuilder.toString());
            JSONArray choices = responseJson.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    return message.optString("content", "Sorry, no content in response.");
                }
            }
            return "Sorry, no valid response from API.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to get response from OpenRouter API: " + e.getMessage();
        }
    }

    private void loadUserChatHistory() {
        chatHistory.clear();
        clearChatPane();

        File userFile = new File(userDataDir, "user_" + sanitizeFilename(currentUser) + ".json");
        if (userFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject msgObj = arr.getJSONObject(i);
                    String role = msgObj.getString("role");
                    String content = msgObj.getString("content");
                    chatHistory.add(new Message(role, content));
                    if ("user".equals(role)) {
                        appendMessage("You: " + content + "\n", userStyle);
                    } else {
                        appendMessage("VetBot: " + content + "\n", botStyle);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                appendBotMessage("Failed to load your previous chat history.");
            }
        }
    }

    private void saveUserChatHistory() {
        if (currentUser == null) return;

        File userFile = new File(userDataDir, "user_" + sanitizeFilename(currentUser) + ".json");
        JSONArray arr = new JSONArray();
        for (Message msg : chatHistory) {
            JSONObject obj = new JSONObject();
            obj.put("role", msg.getRole());
            obj.put("content", msg.getContent());
            arr.put(obj);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile))) {
            writer.write(arr.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    static class Message {
        private String role; // "user" or "bot"
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VetClinicChatbot_2 chatbot = new VetClinicChatbot_2();
            chatbot.setVisible(true);
            // Add shutdown hook to save chat history on exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                chatbot.saveUserChatHistory();
            }));
        });
    }
}
