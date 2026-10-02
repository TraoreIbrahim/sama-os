# Environnement de construction de l'ISO Sama OS : Debian 13 avec live-build.
FROM debian:trixie

RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
		live-build \
		debootstrap \
		ca-certificates \
		xorriso \
		squashfs-tools \
		mtools \
		dosfstools \
		zstd \
		librsvg2-bin \
		grub-common \
	&& rm -rf /var/lib/apt/lists/*

WORKDIR /construction
