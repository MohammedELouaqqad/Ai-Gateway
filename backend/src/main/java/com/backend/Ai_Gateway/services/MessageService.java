package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.entity.Attachment;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(MessageRepository messageRepository, UserRepository userRepository, ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public void modifyDate(Long conversationId){
        Conversation conversation=conversationRepository.findById(conversationId)
                .orElseThrow(()-> new RessourceNotFoundException("Conversation not found"));
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public Message createUserMessage(String contenu,Long conversationId, Long userId){
        return createUserMessage(contenu, conversationId, userId, null);
    }

    /** {@code attachment} nullable — see {@link Attachment} javadoc for the "at most one per
     *  message" scoping. */
    @Transactional
    public Message createUserMessage(String contenu, Long conversationId, Long userId, Attachment attachment){
        Conversation conversation= conversationRepository.findById(conversationId)
                .orElseThrow(()-> new RessourceNotFoundException("Conversation not found"));
        if(conversation.getUser().getId().equals(userId)){
            Message message=new Message();
            message.setConversation(conversation);
            message.setContent(contenu);
            message.setRole(MessageRole.USER);
            message.setCreatedAt(Instant.now());
            message.setAttachment(attachment);
            modifyDate(conversation.getId());
            messageRepository.save(message);
            return message;
        }
        throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce message");
    }


    /** @deprecated kept for callers that don't track RAG usage; delegates with ragChunksUsed=0. */
    @Deprecated
    @Transactional
    public Message createAssistantMessage(String contenu, Long conversationId, Long userId, Model model) {
        return createAssistantMessage(contenu, conversationId, userId, model, 0);
    }

    @Transactional
    public Message createAssistantMessage(String contenu, Long conversationId, Long userId, Model model, int ragChunksUsed) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        if (conversation.getUser().getId().equals(userId)) {
            Message message = new Message();
            message.setConversation(conversation);
            message.setContent(contenu);
            message.setRole(MessageRole.ASSISTANT);
            message.setCreatedAt(Instant.now());
            message.setRagChunksUsed(ragChunksUsed);
            if (model != null) {
                message.setModelName(model.getName());
                message.setModelDisplayName(
                        model.getDisplayName() != null && !model.getDisplayName().isBlank()
                                ? model.getDisplayName()
                                : model.getName()
                );
            }
            modifyDate(conversation.getId());
            messageRepository.save(message);
            return message;
        }
        throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce message");
    }

    public List<Message> getMessagesByConversationId(Long userId, Long conversationId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        if (conversation.getUser() == null || !conversation.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
        }
        return messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(conversationId);
    }

    public Message requireOwnedAssistantMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RessourceNotFoundException("Message not found"));
        if (message.getConversation() == null
                || message.getConversation().getUser() == null
                || !message.getConversation().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce message");
        }
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new IllegalArgumentException("message: only assistant messages can be regenerated");
        }
        return message;
    }

    public Message findPrecedingUserMessage(Message assistant) {
        List<Message> all = messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(
                assistant.getConversation().getId());
        Message previousUser = null;
        for (Message m : all) {
            if (m.getId().equals(assistant.getId())) {
                break;
            }
            if (m.getRole() == MessageRole.USER) {
                previousUser = m;
            }
        }
        if (previousUser == null) {
            throw new IllegalArgumentException("message: no preceding user message to regenerate");
        }
        return previousUser;
    }

    @Transactional
    public void markRegenerated(Message oldAssistant, Long newAssistantId) {
        oldAssistant.setRegenerated(true);
        oldAssistant.setSupersededByMessageId(newAssistantId);
        messageRepository.save(oldAssistant);
    }

    public java.util.Optional<Message> findLatestAssistantMessage(Long conversationId) {
        List<Message> all = messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(conversationId);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).getRole() == MessageRole.ASSISTANT && !all.get(i).isRegenerated()) {
                return java.util.Optional.of(all.get(i));
            }
        }
        return java.util.Optional.empty();
    }

}
