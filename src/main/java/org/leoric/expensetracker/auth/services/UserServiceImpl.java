package org.leoric.expensetracker.auth.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.dto.UserInfoResponse;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.mapstruct.UserMapper;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.services.interfaces.UserService;
import org.leoric.expensetracker.handler.exceptions.IncorrectCurrentPasswordException;
import org.leoric.expensetracker.handler.exceptions.NewPasswordDoesNotMatchException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserMapper userMapper;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional(readOnly = true)
	public UserInfoResponse getCurrentUser(User currentUser) {
		currentUser = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new RuntimeException("User not found"));

		return userMapper.userToUserInfoResponse(currentUser);
	}

	@Override
	@Transactional
	public UserResponseFullDto updateProfile(User currentUser, UserProfileUpdateDto dto) {
		User user = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		userMapper.updateUserFromDto(dto, user);

		userRepository.save(user);

		return userMapper.userToUserResponseFull(user);
	}

	@Override
	@Transactional
	public void changePassword(User currentUser, UserPasswordChangeDto dto) {
		User user = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		if (!passwordEncoder.matches(dto.oldPassword(), user.getPassword())) {
			throw new IncorrectCurrentPasswordException("Current password is incorrect");
		}

		if (!dto.newPassword().equals(dto.newConfirmationPassword())) {
			throw new NewPasswordDoesNotMatchException("New password and confirmation do not match");
		}

		user.setPassword(passwordEncoder.encode(dto.newPassword()));
		userRepository.save(user);
	}
}