package com.dulno.access.portal.security.home;

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
public class HomeAuthorizationFilter extends OncePerRequestFilter {
  private final Key homeKey;
  private final EndpointRepository endpointRepository;
  private List<String> targetEndpoints = Lists.newArrayList();

  private HomeAuthorizationFilter(
    @Qualifier("homeKey") Key homeKey, EndpointRepository endpointRepository
  ) {
    this.homeKey = homeKey;
    this.endpointRepository = endpointRepository;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var apiKey = request.getHeader("Home-Authorization");
    if (apiKey == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    apiKey = apiKey.replace("Bearer ", "");
    if (!validateApiKey(apiKey)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var url = request.getRequestURI();
    return !targetEndpoints.contains(url);
  }

  private boolean validateApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(homeKey)
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
      .findAnnotatedEndpoints(HomeEndpoint.class)
      .stream().map(Endpoint::path).toList();
  }
}
