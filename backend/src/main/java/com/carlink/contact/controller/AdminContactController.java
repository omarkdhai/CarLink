package com.carlink.contact.controller;

import com.carlink.contact.dto.AdminContactView;
import com.carlink.contact.model.ContactMessage;
import com.carlink.contact.repository.ContactMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/contacts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContactController {

    private final ContactMessageRepository repository;

    public AdminContactController(ContactMessageRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<Page<AdminContactView>> list(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<ContactMessage> page = StringUtils.hasText(q)
                ? repository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrReasonContainingIgnoreCaseOrNoteContainingIgnoreCase(
                        q, q, q, q, pageable)
                : repository.findAll(pageable);
        Page<AdminContactView> view = page.map(c -> new AdminContactView(
                c.getId().toString(), c.getName(), c.getEmail(), c.getMobile(), c.getReason(), c.getNote(), c.getCreatedAt()));
        return ResponseEntity.ok(view);
    }
}
