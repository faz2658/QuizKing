import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AiDrafts } from './ai-drafts';

describe('AiDrafts', () => {
  it('lists pending drafts with the answer highlighted and approves them', async () => {
    TestBed.configureTestingModule({ imports: [AiDrafts], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(AiDrafts);
    f.componentRef.setInput('categories', [{ categoryId: 1, categoryName: 'Geography', topic: [{ topicId: 1, topicName: 'Geography', questionCount: 3 }] }]);
    f.detectChanges();

    http.expectOne('/api/v1/question-generator/ai/status').flush({ available: true });
    http.expectOne('/api/v1/question-generator/ai/drafts').flush([{
      id: 'd1', categoryId: 1, topicName: 'Geography', status: 'PENDING', createdAt: '',
      question: { questionStem: 'Capital of Peru?', questionLevel: 2, option1: 'Quito', option2: 'Lima', option3: 'Cusco', option4: 'La Paz', correctAnswer: 'Lima' },
    }]);
    f.detectChanges();
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;

    expect(el.textContent).toContain('1 waiting for review');
    expect(el.querySelector('.opt.answer')!.textContent).toBe('Lima');
    expect(el.textContent).toContain('Model connected');

    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.trim() === 'Approve') as HTMLButtonElement).click();
    const req = http.expectOne('/api/v1/question-generator/ai/drafts/d1/approve');
    expect(req.request.body.correctAnswer).toBe('Lima');
    req.flush({});
    f.detectChanges();

    expect(el.textContent).not.toContain('waiting for review');
  });
});
