package com.luna.aggarly.wishlist.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.wishlist.dto.*;
import com.luna.aggarly.wishlist.entity.Wishlist;
import com.luna.aggarly.wishlist.entity.WishlistItem;
import com.luna.aggarly.wishlist.exceptions.DuplicateWishlistItemException;
import com.luna.aggarly.wishlist.exceptions.WishlistNotFoundException;
import com.luna.aggarly.wishlist.mapper.WishlistMapper;
import com.luna.aggarly.wishlist.repository.WishlistItemRepository;
import com.luna.aggarly.wishlist.repository.WishlistRepository;
import com.luna.aggarly.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository itemRepository;
    private final PropertyService propertyService;
    private final WishlistMapper wishlistMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public WishlistResponse createWishlist(CreateWishlistRequest request, UUID userId) {
        log.info("Creating wishlist '{}' for userId={}", request.name(), userId);
        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .name(request.name())
                .description(request.description())
                .isPublic(request.isPublic())
                .build();

        wishlist = wishlistRepository.save(wishlist);
        return wishlistMapper.toResponse(wishlist, 0, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistResponse> getUserWishlists(UUID userId) {
        return wishlistRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(wishlist -> {
                    long count = itemRepository.countByWishlistId(wishlist.getId());
                    return wishlistMapper.toResponse(wishlist, count, null);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistResponse getWishlistById(UUID wishlistId, UUID userId) {
        Wishlist wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new WishlistNotFoundException(wishlistId));

        if (!wishlist.isPublic() && (userId == null || !wishlist.getUserId().equals(userId))) {
            throw new WishlistNotFoundException(wishlistId);
        }

        List<WishlistItem> items = itemRepository.findByWishlistIdOrderByCreatedAtDesc(wishlistId);
        List<WishlistItemResponse> itemResponses = items.stream()
                .map(item -> {
                    PropertyResponse prop = null;
                    try {
                        prop = propertyService.getPropertyById(item.getPropertyId());
                    } catch (Exception e) {
                        log.warn("Property {} not found for wishlist item {}", item.getPropertyId(), item.getId());
                    }
                    return wishlistMapper.toItemResponse(item, prop);
                })
                .toList();

        return wishlistMapper.toResponse(wishlist, itemResponses.size(), itemResponses);
    }

    @Override
    @Transactional
    public WishlistResponse updateWishlist(UUID wishlistId, UpdateWishlistRequest request, UUID userId) {
        Wishlist wishlist = wishlistRepository.findByIdAndUserId(wishlistId, userId)
                .orElseThrow(() -> new WishlistNotFoundException(wishlistId));

        wishlist.setName(request.name());
        wishlist.setDescription(request.description());
        wishlist.setPublic(request.isPublic());
        wishlistRepository.save(wishlist);

        long count = itemRepository.countByWishlistId(wishlistId);
        return wishlistMapper.toResponse(wishlist, count, null);
    }

    @Override
    @Transactional
    public void deleteWishlist(UUID wishlistId, UUID userId) {
        Wishlist wishlist = wishlistRepository.findByIdAndUserId(wishlistId, userId)
                .orElseThrow(() -> new WishlistNotFoundException(wishlistId));

        wishlistRepository.delete(wishlist);
    }

    @Override
    @Transactional
    public WishlistItemResponse addPropertyToWishlist(UUID wishlistId, UUID propertyId, UUID userId) {
        Wishlist wishlist = wishlistRepository.findByIdAndUserId(wishlistId, userId)
                .orElseThrow(() -> new WishlistNotFoundException(wishlistId));

        if (itemRepository.existsByWishlistIdAndPropertyId(wishlistId, propertyId)) {
            throw new DuplicateWishlistItemException(propertyId);
        }

        PropertyResponse property = propertyService.getPropertyById(propertyId);

        WishlistItem item = WishlistItem.builder()
                .wishlistId(wishlist.getId())
                .propertyId(propertyId)
                .build();

        item = itemRepository.save(item);
        return wishlistMapper.toItemResponse(item, property);
    }

    @Override
    @Transactional
    public void removePropertyFromWishlist(UUID wishlistId, UUID propertyId, UUID userId) {
        Wishlist wishlist = wishlistRepository.findByIdAndUserId(wishlistId, userId)
                .orElseThrow(() -> new WishlistNotFoundException(wishlistId));

        WishlistItem item = itemRepository.findByWishlistIdAndPropertyId(wishlist.getId(), propertyId)
                .orElseThrow(() -> new WishlistNotFoundException(wishlistId));

        itemRepository.delete(item);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPropertyInUserWishlist(UUID userId, UUID propertyId) {
        if (userId == null) return false;
        List<Wishlist> userWishlists = wishlistRepository.findByUserIdOrderByNameAsc(userId);
        return userWishlists.stream()
                .anyMatch(wl -> itemRepository.existsByWishlistIdAndPropertyId(wl.getId(), propertyId));
    }
}
