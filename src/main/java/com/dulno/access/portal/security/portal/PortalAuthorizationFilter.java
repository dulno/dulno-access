package com.dulno.access.portal.security.portal;

import com.dulno.access.security.Endpoint;
import com.dulno.access.security.EndpointRepository;
import com.google.common.collect.Lists;
import io.jsonwebtoken.ExpiredJwtException;
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
public class PortalAuthorizationFilter extends OncePerRequestFilter {
  private final Key productKey;
  private final EndpointRepository endpointRepository;
  private List<String> targetEndpoints = Lists.newArrayList();

  private PortalAuthorizationFilter(
    @Qualifier("productKey") Key productKey,
    EndpointRepository endpointRepository
  ) {
    this.productKey = productKey;
    this.endpointRepository = endpointRepository;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var apiKey = request.getHeader("Authorization");
    if (apiKey == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    apiKey = apiKey.replace("Bearer ", "");
    var validation = validateApiKey(apiKey);
    if (validation != HttpServletResponse.SC_ACCEPTED) {
      response.setStatus(validation);
      return;
    }
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var url = request.getRequestURI();
    return !targetEndpoints.contains(url);
  }

  private int validateApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(productKey)
        .build()
        .parseClaimsJws(apiKey);
      return HttpServletResponse.SC_ACCEPTED;
    } catch (ExpiredJwtException exception) {
      return HttpServletResponse.SC_EXPECTATION_FAILED;
    } catch (Exception exception) {
      return HttpServletResponse.SC_FORBIDDEN;
    }
  }

  @PostConstruct
  private void findTargetEndpoints() {
    targetEndpoints = endpointRepository
      .findAnnotatedEndpoints(PortalEndpoint.class)
      .stream().map(Endpoint::path).toList();
  }
}