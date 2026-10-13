package com.meridian.platform.notification.infrastructure.adapter.out.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JavaMailEmailSenderAdapterTest {
    @Test
    void acceptsMailAndSurfacesProviderRejection() throws Exception {
        exercise(false, false);
        exercise(true, false);
    }

    @Test
    void refusesPlaintextServerBeforeSendingCredentialsOrMessageWhenTlsIsRequired() throws Exception {
        exercise(false, true);
    }

    private void exercise(boolean reject, boolean requireTls) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            var lines = new ArrayList<String>();
            var completion = executor.submit(() -> {
                try (var socket = server.accept();
                     var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     var writer = new PrintWriter(socket.getOutputStream(), true)) {
                    socket.setSoTimeout(3000);
                    writer.print("220 synthetic SMTP\r\n"); writer.flush();
                    boolean data = false;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                        if (data && !line.equals(".")) continue;
                        if (data) { data = false; writer.print("250 accepted\r\n"); }
                        else if (line.startsWith("EHLO")) writer.print("250 synthetic\r\n");
                        else if (line.startsWith("RCPT") && reject) writer.print("550 synthetic rejection\r\n");
                        else if (line.equals("DATA")) { data = true; writer.print("354 send message\r\n"); }
                        else if (line.equals("QUIT")) { writer.print("221 bye\r\n"); writer.flush(); break; }
                        else writer.print("250 OK\r\n");
                        writer.flush();
                    }
                }
                return null;
            });
            var sender = new JavaMailSenderImpl();
            sender.setHost(server.getInetAddress().getHostAddress()); sender.setPort(server.getLocalPort());
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "2000");
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "2000");
            sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", Boolean.toString(requireTls));
            sender.getJavaMailProperties().setProperty("mail.smtp.starttls.required", Boolean.toString(requireTls));
            var adapter = new JavaMailEmailSenderAdapter(sender);
            if (reject || requireTls) assertThrows(MailException.class,
                    () -> adapter.send("sender@example.invalid", "recipient@example.invalid", "Fixture", "Synthetic body"));
            else adapter.send("sender@example.invalid", "recipient@example.invalid", "Fixture", "Synthetic body");
            completion.get(5, TimeUnit.SECONDS);
            if (requireTls) {
                assertFalse(lines.stream().anyMatch(line -> line.startsWith("AUTH") || line.startsWith("MAIL") || line.equals("DATA")));
            } else if (!reject) assertTrue(lines.contains("Synthetic body"));
        }
    }
}
