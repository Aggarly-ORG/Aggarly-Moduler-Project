package com.luna.aggarly.user.service.impl;

import jakarta.persistence.EntityNotFoundException;
import com.luna.aggarly.user.dto.SavePaymentMethodRequest;
import com.luna.aggarly.user.dto.UserPaymentMethodResponse;
import com.luna.aggarly.user.entity.UserPaymentMethod;
import com.luna.aggarly.user.repository.UserPaymentMethodRepository;
import com.luna.aggarly.user.service.UserPaymentMethodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPaymentMethodServiceImpl implements UserPaymentMethodService {

    private final UserPaymentMethodRepository paymentMethodRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserPaymentMethodResponse> getUserPaymentMethods(UUID userId) {
        return paymentMethodRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(UserPaymentMethodResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserPaymentMethodResponse savePaymentMethod(UUID userId, SavePaymentMethodRequest request) {
        // If this is marked as default or is the user's first card, unset any previous defaults
        List<UserPaymentMethod> existing = paymentMethodRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        boolean makeDefault = request.isDefault() || existing.isEmpty();

        if (makeDefault) {
            existing.forEach(card -> {
                if (card.isDefault()) {
                    card.setDefault(false);
                    paymentMethodRepository.save(card);
                }
            });
        }

        // Check if already stored with this stripe PM ID
        UserPaymentMethod entity = paymentMethodRepository
                .findByUserIdAndStripePaymentMethodId(userId, request.getStripePaymentMethodId())
                .orElseGet(() -> UserPaymentMethod.builder()
                        .userId(userId)
                        .stripePaymentMethodId(request.getStripePaymentMethodId())
                        .build());

        entity.setCardBrand(request.getCardBrand().toLowerCase());
        entity.setLastFour(request.getLastFour());
        entity.setExpMonth(request.getExpMonth());
        entity.setExpYear(request.getExpYear());
        entity.setCardholderName(request.getCardholderName());
        entity.setDefault(makeDefault);

        UserPaymentMethod saved = paymentMethodRepository.save(entity);
        log.info("Saved user payment method: user={}, cardId={}, brand={}, last4={}",
                userId, saved.getId(), saved.getCardBrand(), saved.getLastFour());

        return UserPaymentMethodResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deletePaymentMethod(UUID userId, UUID paymentMethodId) {
        UserPaymentMethod entity = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Payment method not found: " + paymentMethodId));

        paymentMethodRepository.delete(entity);
        log.info("Deleted payment method: user={}, cardId={}", userId, paymentMethodId);
    }

    @Override
    @Transactional
    public void setDefaultPaymentMethod(UUID userId, UUID paymentMethodId) {
        List<UserPaymentMethod> all = paymentMethodRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        boolean found = false;

        for (UserPaymentMethod card : all) {
            if (card.getId().equals(paymentMethodId)) {
                card.setDefault(true);
                found = true;
            } else if (card.isDefault()) {
                card.setDefault(false);
            }
        }

        if (!found) {
            throw new EntityNotFoundException("Payment method not found: " + paymentMethodId);
        }

        paymentMethodRepository.saveAll(all);
    }
}
