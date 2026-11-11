# Local Compose only: compiled contracts plus the LOCAL_SIMULATION deploy and bootstrap scripts. No key is baked in or used.
FROM ghcr.io/foundry-rs/foundry:v1.8.7
USER root
WORKDIR /work
COPY . .
# Windows checkouts may carry CRLF, which bash refuses.
RUN sed -i 's/\r$//' scripts/*.sh docker/*.sh && mkdir -p deployments && chown -R foundry:foundry /work
USER foundry
RUN bash scripts/install-deps.sh && forge build
ENTRYPOINT ["bash"]
