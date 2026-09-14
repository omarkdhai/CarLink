package com.carlink.admin.service;

import com.carlink.admin.model.Report;
import com.carlink.conversation.model.Message;
import com.carlink.conversation.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders moderation reports as a UTF-8 CSV for admin export. Hand-rolled on
 * purpose: one fixed column set does not justify a CSV dependency. Every cell
 * goes through {@link #escape} to neutralise spreadsheet formula injection.
 */
@Component
@RequiredArgsConstructor
public class ReportCsvExporter {

    private final MessageRepository messageRepository;

    public byte[] toCsv(List<Report> reports) {
        String header = "id,createdAt,reason,status,reporterIp,conversationId,"
                + "channel,vehicleNickname,messageCount,lastMessageContent,details\r\n";
        StringBuilder sb = new StringBuilder(header);
        for (Report report : reports) {
            var messages = messages(report);
            Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            sb.append(escape(String.valueOf(report.getId()))).append(',')
                    .append(escape(String.valueOf(report.getCreatedAt()))).append(',')
                    .append(escape(report.getReason().name())).append(',')
                    .append(escape(report.getStatus().name())).append(',')
                    .append(escape(report.getReporterIp())).append(',')
                    .append(escape(report.getConversation() == null ? null
                            : String.valueOf(report.getConversation().getId()))).append(',')
                    .append(escape(report.getConversation() == null ? null
                            : report.getConversation().getChannel().name())).append(',')
                    .append(escape(report.getConversation() == null ? null
                            : report.getConversation().getVehicle().getNickname())).append(',')
                    .append(escape(String.valueOf(messages.size())))
                    .append(',')
                    .append(escape(last == null ? null : last.getContent()))
                    .append(',')
                    .append(escape(report.getDetails()))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<Message> messages(Report report) {
        if (report.getConversation() == null) {
            return List.of();
        }
        return messageRepository
                .findAllByConversation_IdOrderByCreatedAtAsc(report.getConversation().getId());
    }

    /**
     * CSV-cell escaping + formula-injection defence: a cell whose content
     * starts with {@code = + - @ \t \r} gets a leading quote so spreadsheets
     * don't execute it; cells containing commas, quotes, CR, or LF are
     * quoted with embedded quotes doubled.
     */
    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        String cell = raw;
        if (!cell.isEmpty() && "+-=@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        boolean needsQuotes = cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\r') >= 0 || cell.indexOf('\n') >= 0;
        if (!needsQuotes) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}