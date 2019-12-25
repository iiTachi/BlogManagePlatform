package frodez.constant.keys.cache;

import lombok.experimental.UtilityClass;

/**
 * 缓存key标志常量<br>
 * 使用缓存时,应在key前面拼接常量中合适的标志.<br>
 * 每一种用途都建立自己的内部类,在里面进行管理.<br>
 * <strong>key不要重复!!!</strong>
 * @author Frodez
 * @date 2018-12-21
 */
@UtilityClass
public class CacheKey {

	public static class Checker {

		public static final String AUTO = "Checker.key.auto";

		public static final String MANUAL = "Checker.key.manual";

	}

	public static class NameCache {

		public static final String KEY = "NameCache.key";

	}

	public static class TokenCache {

		public static final String KEY = "TokenCache.key";

		public static final String ID = "TokenCache.id";

	}

	public static class UserIdCache {

		public static final String KEY = "UserIdCache.key";

	}

}
