import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { QuizStage } from './quiz-stage';

describe('QuizStage', () => {
  let fixture: ComponentFixture<QuizStage>;
  let chosen: string[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [QuizStage] }).compileComponents();
    fixture = TestBed.createComponent(QuizStage);
    chosen = [];
    fixture.componentInstance.choose.subscribe((o) => chosen.push(o));
    fixture.componentRef.setInput('question', {
      questionId: 1, number: 2, stem: 'Capital of Peru?', options: ['Quito', 'Lima', 'Bogotá'], secondsRemaining: 9, points: 20,
    });
    fixture.componentRef.setInput('total', 5);
    fixture.componentRef.setInput('secondsLeft', 9);
    fixture.componentRef.setInput('fraction', 0.9);
    fixture.detectChanges();
  });

  const buttons = () => Array.from(fixture.nativeElement.querySelectorAll('button.option')) as HTMLButtonElement[];

  it('renders the question and its options', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Capital of Peru?');
    expect(text).toContain('Question 2');
    expect(buttons().map((b) => b.querySelector('.label')?.textContent)).toEqual(['Quito', 'Lima', 'Bogotá']);
  });

  it('emits the clicked option and supports number keys', () => {
    buttons()[1].click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '3' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '9' }));
    expect(chosen).toEqual(['Lima', 'Bogotá']);
  });

  it('marks the right answer and the wrong pick after answering, and locks the options', () => {
    fixture.componentRef.setInput('selected', 'Quito');
    fixture.componentRef.setInput('feedback', { correct: false, timedOut: false, correctAnswer: 'Lima', points: 0 });
    fixture.componentRef.setInput('locked', true);
    fixture.detectChanges();

    expect(buttons()[0].classList).toContain('wrong');
    expect(buttons()[1].classList).toContain('right');
    expect(buttons().every((b) => b.disabled)).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('it was Lima');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: '2' }));
    expect(chosen).toEqual([]);
  });

  it('offers Next once the next question is ready, by button, Enter or Space', () => {
    let nexts = 0;
    fixture.componentInstance.next.subscribe(() => nexts++);
    fixture.componentRef.setInput('selected', 'Quito');
    fixture.componentRef.setInput('feedback', { correct: false, timedOut: false, correctAnswer: 'Lima', points: 0 });
    fixture.componentRef.setInput('locked', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button.next')).toBeNull(); // the answer isn't back yet
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(nexts).toBe(0);

    fixture.componentRef.setInput('canAdvance', true);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button.next') as HTMLButtonElement).click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));
    expect(nexts).toBe(3);
    expect(chosen).toEqual([]); // and no option got picked by those keys
  });
});
