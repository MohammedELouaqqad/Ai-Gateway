package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
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
        Conversation conversation= conversationRepository.findById(conversationId)
                .orElseThrow(()-> new RessourceNotFoundException("Conversation not found"));
        if(conversation.getUser().getId().equals(userId)){
            Message message=new Message();
            message.setConversation(conversation);
            message.setContent(contenu);
            message.setRole(MessageRole.USER);
            message.setCreatedAt(Instant.now());
            modifyDate(conversation.getId());
            messageRepository.save(message);
            return message;
        }
        throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce message");
    }


    @Transactional
    public Message createAssistantMessage(String contenu, Long conversationId, Long userId, Model model) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        if (conversation.getUser().getId().equals(userId)) {
            Message message = new Message();
            message.setConversation(conversation);
            message.setContent(contenu);
            message.setRole(MessageRole.ASSISTANT);
            message.setCreatedAt(Instant.now());
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

}
