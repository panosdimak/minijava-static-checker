JTB_JAR     := jtb133di.jar
JAVACC_JAR  := javacc5.jar
GRAMMAR     := minijava.jj
JTB_OUT     := minijava-jtb.jj

JTB_URL     := https://cgi.di.uoa.gr/~compilers/tools/jtb133di.jar
JAVACC_URL  := https://cgi.di.uoa.gr/~compilers/tools/javacc5.jar
GRAMMAR_URL := http://www.di.uoa.gr/~compilers/project_files/minijava-fixed-2026/minijava.jj

CURL        := curl -L --fail -sS

.PHONY: all compile clean fetchclean

all: compile

compile: $(JTB_JAR) $(JAVACC_JAR) $(GRAMMAR)
	java -jar $(JTB_JAR) -te $(GRAMMAR)
	java -jar $(JAVACC_JAR) $(JTB_OUT)
	javac Main.java

$(JTB_JAR):
	$(CURL) -o $@ $(JTB_URL)

$(JAVACC_JAR):
	$(CURL) -o $@ $(JAVACC_URL)

$(GRAMMAR):
	$(CURL) -o $@ $(GRAMMAR_URL)

clean:
	rm -rf *.class syntaxtree visitor $(JTB_OUT)

fetchclean: clean
	rm -f $(JTB_JAR) $(JAVACC_JAR) $(GRAMMAR)
