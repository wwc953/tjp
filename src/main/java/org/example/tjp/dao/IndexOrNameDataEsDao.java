package org.example.tjp.dao;

import org.example.tjp.bean.IndexOrNameData;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;


public interface IndexOrNameDataEsDao extends ElasticsearchRepository<IndexOrNameData,Integer> {
}
