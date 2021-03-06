package org.springframework.data.rest.webmvc;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.rest.config.RepositoryRestConfiguration;
import org.springframework.data.rest.config.ResourceMapping;
import org.springframework.data.rest.repository.PagingAndSorting;
import org.springframework.data.web.PageableDefaults;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link HandlerMethodArgumentResolver} implementation responsible for inspecting a request for page and sort
 * parameters for use by the repositories.
 *
 * @author Jon Brisbin
 */
public class PagingAndSortingMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private static final int DEFAULT_PAGE = 1; // We're 1-based, not 0-based
	@Autowired
	private RepositoryRestConfiguration                        config;
	@Autowired
	private RepositoryInformationHandlerMethodArgumentResolver repoInfoResolver;

	public PagingAndSortingMethodArgumentResolver() {
	}

	public PagingAndSortingMethodArgumentResolver(RepositoryRestConfiguration config) {
		this.config = config;
	}

	public RepositoryRestConfiguration getConfig() {
		return config;
	}

	public RepositoryInformationHandlerMethodArgumentResolver getRepoInfoResolver() {
		return repoInfoResolver;
	}

	public PagingAndSortingMethodArgumentResolver setRepoInfoResolver(RepositoryInformationHandlerMethodArgumentResolver repoInfoResolver) {
		this.repoInfoResolver = repoInfoResolver;
		return this;
	}

	@Override public boolean supportsParameter(MethodParameter parameter) {
		return ClassUtils.isAssignable(parameter.getParameterType(), PagingAndSorting.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter,
	                              ModelAndViewContainer mavContainer,
	                              NativeWebRequest webRequest,
	                              WebDataBinderFactory binderFactory) throws Exception {
		HttpServletRequest request = (HttpServletRequest)webRequest.getNativeRequest();

		PageRequest pr = null;
		for(Annotation annotation : parameter.getParameterAnnotations()) {
			if(annotation instanceof PageableDefaults) {
				PageableDefaults defaults = (PageableDefaults)annotation;
				pr = new PageRequest(defaults.pageNumber(), defaults.value());
				break;
			}
		}
		if(null == pr) {
			int page = DEFAULT_PAGE;
			String sPage = request.getParameter(config.getPageParamName());
			if(StringUtils.hasText(sPage)) {
				try {
					page = Integer.parseInt(sPage);
				} catch(NumberFormatException ignored) {
				}
			}
			int limit = config.getDefaultPageSize();
			String sLimit = request.getParameter(config.getLimitParamName());
			if(StringUtils.hasText(sLimit)) {
				try {
					limit = Math.min(Integer.parseInt(sLimit), config.getMaxPageSize());
				} catch(NumberFormatException ignored) {
				}
			}

			Sort sort = null;
			List<Sort.Order> orders = new ArrayList<Sort.Order>();
			String[] orderValues = request.getParameterValues(config.getSortParamName());
			if(null != orderValues) {
				for(String orderParam : orderValues) {
					String name = nameForParam(parameter,
					                           mavContainer,
					                           webRequest,
					                           binderFactory,
					                           orderParam);
					String sortDir = request.getParameter(orderParam + ".dir");
					Sort.Direction dir = (null != sortDir ? Sort.Direction.valueOf(sortDir.toUpperCase()) : Sort.Direction.ASC);
					orders.add(new Sort.Order(dir, name));
				}
				if(!orders.isEmpty()) {
					sort = new Sort(orders);
				}
			}

			if(null != sort) {
				pr = new PageRequest(page - 1, limit, sort);
			} else {
				pr = new PageRequest(page - 1, limit);
			}
		}

		return new PagingAndSorting(config, pr);
	}

	private String nameForParam(MethodParameter parameter,
	                            ModelAndViewContainer mavContainer,
	                            NativeWebRequest webRequest,
	                            WebDataBinderFactory binderFactory,
	                            String orderParam) throws Exception {
		if(null == repoInfoResolver) {
			return orderParam;
		}
		RepositoryInformation repoInfo = (RepositoryInformation)repoInfoResolver.resolveArgument(parameter,
		                                                                                         mavContainer,
		                                                                                         webRequest,
		                                                                                         binderFactory);
		ResourceMapping repoMapping = config.getResourceMappingForDomainType(repoInfo.getDomainType());
		if(null != repoMapping) {
			return repoMapping.getNameForPath(orderParam);
		}
		return orderParam;
	}

}
