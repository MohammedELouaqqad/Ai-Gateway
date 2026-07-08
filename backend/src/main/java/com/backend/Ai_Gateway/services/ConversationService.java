package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.entity.Conversation;
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
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository, UserRepository userRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    public Conversation createConversation(Long userId){
        Optional<User> user= userRepository.findById(userId);
        if(user.isPresent()){
            Conversation conversation=new Conversation();
            conversation.setUser(user.get());
            conversation.setTitle("Nouvelle Conversation");
            conversation.setCreatedAt(Instant.now());
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
            return conversation;
        }
        throw new UnauthorizedException("Ce User est introuvable");
    }

    public List<Conversation> getConversationsByUserId(Long userId){
        userRepository.findById(userId)
                .orElseThrow(()-> new RessourceNotFoundException("User not found"));
        return conversationRepository.findByUser_IdOrderByUpdatedAtDesc(userId);
    }


    public Conversation renameConversation(Long userConnected, Long conversationId, String title){
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title: must not be blank");
        }
        if(verifyProprty(userConnected, conversationId)){
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
            conversation.setTitle(title);
            conversation.setUpdatedAt(Instant.now());
            return conversationRepository.save(conversation);
        }
        throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
    }

    @Transactional
    public void deleteConversation(Long userConnected,Long idConversation){
        if(verifyProprty(userConnected,idConversation)){
            messageRepository.deleteByConversation_Id(idConversation);
            conversationRepository.deleteById(idConversation);
        }else {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
        }
    }

    public boolean verifyProprty(Long userConnected, Long conversationId){
        Conversation conversation= conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        userRepository.findById(userConnected)
                .orElseThrow(()-> new RessourceNotFoundException("user not found"));
        return conversation.getUser() != null
                && conversation.getUser().getId().equals(userConnected);
    }
}
