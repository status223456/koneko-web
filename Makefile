# Same commands as the .bat files, for Linux and macOS.

.PHONY: build run dev clean

build:
	./gradlew shadowJar

run: build
	@test -f .env || cp .env.example .env
	@test -f config.yml || cp config.example.yml config.yml
	java -jar build/libs/koneko-web-shaded.jar

dev:
	@test -f .env || cp .env.example .env
	@test -f config.yml || cp config.example.yml config.yml
	./gradlew run --console=plain

clean:
	./gradlew clean
