FROM nginx:latest

# Remove the default config
RUN rm /etc/nginx/conf.d/default.conf

# Copy your custom Nginx configuration
COPY ./nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

