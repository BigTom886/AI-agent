package com.wc.rag;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

@Component
public class MyTokenTextSplitter
{
    public List<Document> spliteDocument(List<Document> documents)
    {
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        return tokenTextSplitter.apply(documents);
    }

    public List<Document> spliteCustomized(List<Document> documents)
    {
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(200, 100, 10, 5000, true, List.of('。'));
        return tokenTextSplitter.apply(documents);
    }
}
