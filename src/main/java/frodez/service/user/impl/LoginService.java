package frodez.service.user.impl;

import frodez.config.aop.exception.annotation.Error;
import frodez.config.security.util.AuthorityUtil;
import frodez.config.security.util.TokenUtil;
import frodez.constant.errors.code.ErrorCode;
import frodez.dao.param.user.DoLogin;
import frodez.dao.param.user.DoRefresh;
import frodez.dao.result.user.PermissionInfo;
import frodez.dao.result.user.UserInfo;
import frodez.service.cache.facade.TokenCache;
import frodez.service.user.facade.IAuthorityService;
import frodez.service.user.facade.ILoginService;
import frodez.util.beans.result.Result;
import frodez.util.common.StreamUtil;
import frodez.util.spring.MVCUtil;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;

/**
 * 登录管理服务
 * @author Frodez
 * @date 2018-11-14
 */
@Service
@Error(ErrorCode.LOGIN_SERVICE_ERROR)
public class LoginService implements ILoginService {

	/**
	 * spring security验证管理器
	 */
	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private SecurityContextLogoutHandler logoutHandler;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	@Qualifier("tokenRedisCache")
	private TokenCache tokenCache;

	@Autowired
	private IAuthorityService authorityService;

	@Override
	public Result login(DoLogin param) {
		Result result = authorityService.getUserInfo(param.getUsername());
		if (result.unable()) {
			return result;
		}
		UserInfo userInfo = result.as(UserInfo.class);
		if (!passwordEncoder.matches(param.getPassword(), userInfo.getPassword())) {
			return Result.fail("用户名或密码错误");
		}
		if (tokenCache.existId(userInfo.getId())) {
			return Result.fail("用户已登录");
		}
		List<String> authorities = StreamUtil.list(userInfo.getPermissionList(), PermissionInfo::getName);
		//realToken
		String token = TokenUtil.generate(param.getUsername(), authorities);
		tokenCache.save(token, userInfo);
		Authentication authentication = new UsernamePasswordAuthenticationToken(param.getUsername(), param.getPassword());
		authentication = authenticationManager.authenticate(authentication);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return Result.success(token);
	}

	@Override
	public Result refresh(DoRefresh param) {
		UserDetails userDetails = null;
		//判断token是否能通过验证(不需要验证超时)
		try {
			userDetails = TokenUtil.verifyWithNoExpired(param.getOldToken());
		} catch (Exception e) {
			return Result.noAuth();
		}
		//判断验证通过的token中姓名与填写的姓名是否一致
		if (userDetails == null || !param.getUsername().equals(userDetails.getUsername())) {
			return Result.noAuth();
		}
		//判断token对应账号信息是否存在
		UserInfo tokenUserInfo = tokenCache.get(param.getOldToken());
		if (tokenUserInfo == null) {
			return Result.noAuth();
		}
		//判断姓名对应账号是否正常
		Result result = authorityService.getUserInfo(param.getUsername());
		if (result.unable()) {
			return result;
		}
		UserInfo userInfo = result.as(UserInfo.class);
		//判断token对应账号信息和查询出的账号信息是否对应
		if (!userInfo.getName().equals(tokenUserInfo.getName()) || !userInfo.getPassword().equals(tokenUserInfo.getPassword())) {
			return Result.fail("用户名或密码错误");
		}
		//判断结束
		//生成新token
		String newToken = TokenUtil.generate(userDetails);
		//存入cache
		tokenCache.remove(param.getOldToken());
		tokenCache.save(newToken, userInfo);
		//登出
		logoutHandler.logout(MVCUtil.request(), MVCUtil.response(), SecurityContextHolder.getContext().getAuthentication());
		//登入
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, AuthorityUtil.make(userInfo
			.getPermissionList()));
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(MVCUtil.request()));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return Result.success(newToken);
	}

	@Override
	public Result logout() {
		HttpServletRequest request = MVCUtil.request();
		String token = TokenUtil.getRealToken(request);
		if (!tokenCache.existToken(token)) {
			return Result.fail("用户已下线");
		}
		tokenCache.remove(token);
		logoutHandler.logout(request, MVCUtil.response(), SecurityContextHolder.getContext().getAuthentication());
		return Result.success();
	}

}
