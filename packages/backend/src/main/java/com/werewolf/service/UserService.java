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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 注册账号密码用户
     */
    @Transactional
    public User register(String username, String password, String email) {
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .loginType(User.LoginType.APP)
                .totalGames(0)
                .winGames(0)
                .rating(1000)
                .build();
        
        return userRepository.save(user);
    }
    
    /**
     * 根据微信 openid 查找或创建用户
     * 
     * @param openid 微信 openid
     * @param unionid 微信 unionid（可能为 null）
     * @param nickName 用户昵称
     * @param avatarUrl 用户头像
     * @return 用户对象和是否为新建用户的元组
     */
    @Transactional
    public User findOrCreateByOpenid(String openid, String unionid, String nickName, String avatarUrl) {
        // 先查找是否存在
        Optional<User> existingUser = userRepository.findByWxOpenid(openid);
        
        if (existingUser.isPresent()) {
            log.debug("找到已存在的微信用户: {}", openid);
            return existingUser.get();
        }
        
        // 创建新用户
        log.info("创建新的微信用户: {}", openid);
        
        // 生成唯一的用户名（微信用户可能没有设置用户名）
        String username = generateUniqueUsername(nickName);
        
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // 随机密码，无法用于账号登录
                .wxOpenid(openid)
                .wxUnionid(unionid)
                .avatarUrl(avatarUrl)
                .loginType(User.LoginType.WECHAT)
                .totalGames(0)
                .winGames(0)
                .rating(1000)
                .build();
        
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
