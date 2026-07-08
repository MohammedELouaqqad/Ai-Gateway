package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    public Message createAssistantMessage(String contenu,Long conversationId, Long userId){
        Conversation conversation= conversationRepository.findById(conversationId)
                .orElseThrow(()-> new RessourceNotFoundException("Conversation not found"));
        if(conversation.getUser().getId().equals(userId)){
            Message message=new Message();
            message.setConversation(conversation);
            message.setContent(contenu);
            message.setRole(MessageRole.ASSISTANT);
            message.setCreatedAt(Instant.now());
            modifyDate(conversation.getId());
            messageRepository.save(message);
            return message;
        }
        throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce message");
    }

    public List<Message> getMessagesByConversationId(Long userId,Long conversationId){
        userRepository.findById(userId)
                .orElseThrow(()-> new RessourceNotFoundException("User not found"));
        return messageRepository.findMessagesByConversationId(conversationId);
    }

}
