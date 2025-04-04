package com.dulno.access.portal.security.mixed;

import com.dulno.access.security.Endpoint;
import com.dulno.access.security.EndpointRepository;
import com.google.common.collect.Lists;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.List;

@Component
public class MixedAuthorizationFilter extends OncePerRequestFilter {
  private final Key productKey;
  private final Key homeKey;
  private final EndpointRepository endpointRepository;
  private List<String> targetEndpoints = Lists.newArrayList();

  private MixedAuthorizationFilter(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    EndpointRepository endpointRepository
  ) {
    this.productKey = productKey;
    this.homeKey = homeKey;
    this.endpointRepository = endpointRepository;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var productAuthorized = checkAuthorization(request, productKey, "Authorization");
    var homeAuthorized = checkAuthorization(request, homeKey, "Home-Authorization");
    if (!productAuthorized && !homeAuthorized) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean checkAuthorization(
    HttpServletRequest request, Key key, String headerName
  ) {
    var apiKey = request.getHeader(headerName);
    if (apiKey == null) {
      return false;
    }
    apiKey = apiKey.replace("Bearer ", "");
    return validateApiKey(key, apiKey);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var url = request.getRequestURI();
    return !targetEndpoints.contains(url);
  }

  private boolean validateApiKey(Key key, String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(apiKey);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  @PostConstruct
  private void findTargetEndpoints() {
    targetEndpoints = endpointRepository
      .findAnnotatedEndpoints(MixedEndpoint.class)
      .stream().map(Endpoint::path).toList();
  }
}
