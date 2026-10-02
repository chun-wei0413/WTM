package com.usethatmeme.application.port.out;

import com.usethatmeme.domain.meme.Meme;
import com.usethatmeme.domain.meme.MemeId;
import java.util.Optional;

public interface MemeRepository {

    Optional<Meme> findById(MemeId id);

    /** Inserts a new meme or updates the status and image of an existing one. */
    void save(Meme meme);
}
