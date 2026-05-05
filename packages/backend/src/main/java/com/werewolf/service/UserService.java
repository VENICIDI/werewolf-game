package com.werewolf.service;

import com.werewolf.entity.User;
import com.werewolf.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    // 玩家默认头像池（注册时随机分配）
    private static final String[] DEFAULT_AVATARS = {
        "/avatars/default-male-warm.png",
        "/avatars/default-male-cool.png",
        "/avatars/default-female-warm.png",
        "/avatars/default-female-cool.png"
    };
    
    /**
     * 注册账号密码用户
     */
    @Transactional
    public User register(String username, String password, String email) {
        String randomAvatar = DEFAULT_AVATARS[ThreadLocalRandom.current().nextInt(DEFAULT_AVATARS.length)];
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .avatarUrl(randomAvatar)
                .loginType(User.LoginType.APP)
                .totalGames(0)
                .winGames(0)
                .rating(1000)
                .build();
        
        return userRepository.save(user);
    }
    
    /**
     * 根据微信 openid 查找或创建用户（兼容老调用，不传手机号）
     */
    @Transactional
    public User findOrCreateByOpenid(String openid, String unionid, String nickName, String avatarUrl) {
        return findOrCreateByOpenid(openid, unionid, nickName, avatarUrl, null);
    }
    
    /**
     * 根据微信 openid 查找或创建用户
     * 
     * @param openid 微信 openid
     * @param unionid 微信 unionid（可能为 null）
     * @param nickName 用户昵称（可能为 null）
     * @param avatarUrl 用户头像（可能为 null）
     * @param phone 手机号（可能为 null）
     * @return 用户对象
     */
    @Transactional
    public User findOrCreateByOpenid(String openid, String unionid, String nickName, String avatarUrl, String phone) {
        // 先查找是否存在
        Optional<User> existingUser = userRepository.findByWxOpenid(openid);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.debug("找到已存在的微信用户: {}", openid);
            boolean dirty = false;
            
            // 手机号：如果本次拿到了新的，且旧的为空或不同，则更新
            if (phone != null && !phone.isEmpty() && !phone.equals(user.getPhone())) {
                user.setPhone(phone);
                dirty = true;
            }
            // 头像：只在老用户仍为空时补上（避免覆盖用户自己选过的）
            if ((user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) 
                    && avatarUrl != null && !avatarUrl.isEmpty()) {
                user.setAvatarUrl(avatarUrl);
                dirty = true;
            }
            // 昵称同理
            if ((user.getNickname() == null || user.getNickname().isEmpty()) 
                    && nickName != null && !nickName.isEmpty()) {
                user.setNickname(nickName);
                dirty = true;
            }
            
            return dirty ? userRepository.save(user) : user;
        }
        
        // 创建新用户
        log.info("创建新的微信用户: openid={}, hasPhone={}", openid, phone != null);
        
        // 生成唯一的用户名（微信用户可能没有设置用户名）
        String username = generateUniqueUsername(nickName);
        
        User user = User.builder()
                .username(username)
                .nickname(nickName) // 原始昵称（可能为 null）
                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // 随机密码，无法用于账号登录
                .wxOpenid(openid)
                .wxUnionid(unionid)
                .phone(phone)
                .avatarUrl(avatarUrl)
                .loginType(User.LoginType.WECHAT)
                .totalGames(0)
                .winGames(0)
                .rating(1000)
                .build();
        
        return userRepository.save(user);
    }
    
    /**
     * 更新用户资料（头像 + 昵称）
     */
    @Transactional
    public User updateProfile(Long userId, String nickname, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        boolean dirty = false;
        if (nickname != null && !nickname.isEmpty()) {
            user.setNickname(nickname);
            dirty = true;
        }
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            user.setAvatarUrl(avatarUrl);
            dirty = true;
        }
        if (!dirty) {
            return user;
        }
        return userRepository.save(user);
    }
    
    /**
     * 生成唯一的用户名
     * 
     * @param nickName 昵称（可能为 null）
     * @return 唯一的用户名
     */
    private String generateUniqueUsername(String nickName) {
        String baseName = (nickName != null && !nickName.isEmpty()) 
                ? nickName 
                : "微信用户";
        
        // 如果昵称已存在，添加随机后缀
        String username = baseName;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseName + "_" + UUID.randomUUID().toString().substring(0, 6);
            suffix++;
            if (suffix > 100) {
                // 防止无限循环
                username = "wx_" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        
        return username;
    }
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
    
    public Optional<User> findByWxOpenid(String openid) {
        return userRepository.findByWxOpenid(openid);
    }
    
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
    
    public boolean existsByWxOpenid(String openid) {
        return userRepository.existsByWxOpenid(openid);
    }
    
    @Transactional
    public User updateUser(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }
}
