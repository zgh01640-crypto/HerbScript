package com.herbscript.recognition.provider;

import java.nio.file.Path;

public interface RecognitionProvider {

    String providerName();

    RecognitionDraftData recognize(Path imagePath);
}
